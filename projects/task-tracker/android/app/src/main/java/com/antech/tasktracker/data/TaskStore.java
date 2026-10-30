package com.antech.tasktracker.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * App-private SQLite database in internal storage (/data/data/com.antech.tasktracker/databases/task-tracker.db).
 * No storage permissions are needed and the file is removed when the app is uninstalled.
 * All methods do disk I/O: call them from a background executor, never the main thread.
 */
public final class TaskStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "task-tracker.db";
    private static final int DB_VERSION = 1;
    private static final String T = "tasks";

    private static volatile TaskStore instance;

    public static TaskStore get(Context context) {
        if (instance == null) {
            synchronized (TaskStore.class) {
                if (instance == null) instance = new TaskStore(context.getApplicationContext());
            }
        }
        return instance;
    }

    private TaskStore(Context appContext) {
        super(appContext, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id TEXT PRIMARY KEY,"
                + "title TEXT NOT NULL,"
                + "notes TEXT,"
                + "due_date TEXT,"
                + "priority TEXT NOT NULL DEFAULT 'MEDIUM',"
                + "done INTEGER NOT NULL DEFAULT 0,"
                + "deleted INTEGER NOT NULL DEFAULT 0,"
                + "version INTEGER,"
                + "updated_at INTEGER NOT NULL,"
                + "dirty INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_tasks_dirty ON " + T + "(dirty)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations go here, step by step with ALTER TABLE; never drop user data.
    }

    /** @param done null = all, otherwise filter by status. Open tasks first, then most recently changed. */
    public List<Task> list(Boolean done) {
        String where = "deleted = 0";
        String[] args = null;
        if (done != null) {
            where += " AND done = ?";
            args = new String[] { done ? "1" : "0" };
        }
        return query(where, args, "done ASC, updated_at DESC");
    }

    public Task find(String id) {
        List<Task> rows = query("id = ?", new String[] { id }, null);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Local create/edit: the row becomes dirty so the next sync uploads it. */
    public Task saveLocal(Task task) {
        if (task.id == null) task.id = UUID.randomUUID().toString();
        task.updatedAt = System.currentTimeMillis();
        task.dirty = true;
        getWritableDatabase().insertWithOnConflict(T, null, toValues(task), SQLiteDatabase.CONFLICT_REPLACE);
        return task;
    }

    /** Never-synced tasks are removed at once; synced ones are soft-deleted so the delete can be uploaded. */
    public void deleteLocal(String id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T, "id = ? AND version IS NULL", new String[] { id });
        ContentValues v = new ContentValues();
        v.put("deleted", 1);
        v.put("dirty", 1);
        v.put("updated_at", System.currentTimeMillis());
        db.update(T, v, "id = ?", new String[] { id });
    }

    public List<Task> dirtyRows() {
        return query("dirty = 1", null, "updated_at ASC");
    }

    /**
     * Marks an uploaded row clean. Only applies if the row was not edited again while the upload was in flight
     * (same updated_at), so no local change is ever lost.
     */
    public void markSynced(Task uploaded, long serverVersion) {
        String[] args = { uploaded.id, String.valueOf(uploaded.updatedAt) };
        SQLiteDatabase db = getWritableDatabase();
        if (uploaded.deleted) {
            db.delete(T, "id = ? AND updated_at = ?", args);
            return;
        }
        ContentValues v = new ContentValues();
        v.put("dirty", 0);
        v.put("version", serverVersion);
        db.update(T, v, "id = ? AND updated_at = ?", args);
    }

    /** Applies a server copy, except over rows that still have unsynced local edits (unless forced). */
    public void applyRemote(Task remote, boolean force) {
        Task local = find(remote.id);
        if (!force && local != null && local.dirty) return;
        SQLiteDatabase db = getWritableDatabase();
        if (remote.deleted) {
            db.delete(T, "id = ?", new String[] { remote.id });
            return;
        }
        remote.dirty = false;
        db.insertWithOnConflict(T, null, toValues(remote), SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeLocal(String id) {
        getWritableDatabase().delete(T, "id = ?", new String[] { id });
    }

    private List<Task> query(String where, String[] args, String orderBy) {
        List<Task> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T, null, where, args, null, null, orderBy)) {
            int iId = c.getColumnIndexOrThrow("id");
            int iTitle = c.getColumnIndexOrThrow("title");
            int iNotes = c.getColumnIndexOrThrow("notes");
            int iDue = c.getColumnIndexOrThrow("due_date");
            int iPrio = c.getColumnIndexOrThrow("priority");
            int iDone = c.getColumnIndexOrThrow("done");
            int iDeleted = c.getColumnIndexOrThrow("deleted");
            int iVersion = c.getColumnIndexOrThrow("version");
            int iUpdated = c.getColumnIndexOrThrow("updated_at");
            int iDirty = c.getColumnIndexOrThrow("dirty");
            while (c.moveToNext()) {
                Task t = new Task();
                t.id = c.getString(iId);
                t.title = c.getString(iTitle);
                t.notes = c.getString(iNotes);
                t.dueDate = c.getString(iDue);
                t.priority = c.getString(iPrio);
                t.done = c.getInt(iDone) == 1;
                t.deleted = c.getInt(iDeleted) == 1;
                t.version = c.isNull(iVersion) ? null : c.getLong(iVersion);
                t.updatedAt = c.getLong(iUpdated);
                t.dirty = c.getInt(iDirty) == 1;
                out.add(t);
            }
        }
        return out;
    }

    private static ContentValues toValues(Task t) {
        ContentValues v = new ContentValues();
        v.put("id", t.id);
        v.put("title", t.title);
        v.put("notes", t.notes);
        v.put("due_date", t.dueDate);
        v.put("priority", t.priority);
        v.put("done", t.done ? 1 : 0);
        v.put("deleted", t.deleted ? 1 : 0);
        if (t.version == null) v.putNull("version"); else v.put("version", t.version);
        v.put("updated_at", t.updatedAt);
        v.put("dirty", t.dirty ? 1 : 0);
        return v;
    }
}
