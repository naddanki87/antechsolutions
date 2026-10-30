package __APP_ID__.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * App-private SQLite database in internal storage (/data/data/<appId>/databases/__SLUG__.db).
 * No storage permissions are required and the file is removed when the app is uninstalled.
 * All methods do disk I/O: call them from a background executor, never the main thread.
 */
public final class LocalStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "__SLUG__.db";
    private static final int DB_VERSION = 1;
    private static final String T = "items";

    private static volatile LocalStore instance;
    private final Context appContext;

    public static LocalStore get(Context context) {
        if (instance == null) {
            synchronized (LocalStore.class) {
                if (instance == null) instance = new LocalStore(context.getApplicationContext());
            }
        }
        return instance;
    }

    private LocalStore(Context appContext) {
        super(appContext, DB_NAME, null, DB_VERSION);
        this.appContext = appContext;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id TEXT PRIMARY KEY,"
                + "title TEXT NOT NULL,"
                + "notes TEXT,"
                + "updated_at INTEGER NOT NULL,"
                + "version INTEGER NOT NULL DEFAULT 0,"
                + "dirty INTEGER NOT NULL DEFAULT 1,"
                + "deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_items_dirty ON " + T + "(dirty)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migrate step by step with ALTER TABLE; never drop user data.
        // if (oldVersion < 2) db.execSQL("ALTER TABLE items ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
    }

    public List<Item> listActive() {
        return query("deleted = 0", null, "updated_at DESC");
    }

    public Item find(String id) {
        List<Item> rows = query("id = ?", new String[] { id }, null);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Local create/edit: marks the row dirty so the next sync uploads it. */
    public Item saveLocal(Item item) {
        if (item.id == null) item.id = UUID.randomUUID().toString();
        item.updatedAt = System.currentTimeMillis();
        item.dirty = true;
        getWritableDatabase().insertWithOnConflict(T, null, toValues(item), SQLiteDatabase.CONFLICT_REPLACE);
        return item;
    }

    /** Soft delete so the deletion can be synced; purge after the server confirms. */
    public void deleteLocal(String id) {
        ContentValues v = new ContentValues();
        v.put("deleted", 1);
        v.put("dirty", 1);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(T, v, "id = ?", new String[] { id });
    }

    public List<Item> dirtyRows() {
        return query("dirty = 1", null, null);
    }

    public void markSynced(String id, long serverVersion) {
        ContentValues v = new ContentValues();
        v.put("dirty", 0);
        v.put("version", serverVersion);
        getWritableDatabase().update(T, v, "id = ?", new String[] { id });
        getWritableDatabase().delete(T, "id = ? AND deleted = 1", new String[] { id });
    }

    /** Server copy wins on pull, except over rows with unsynced local edits. */
    public void applyRemote(Item remote) {
        Item local = find(remote.id);
        if (local != null && local.dirty) return;
        remote.dirty = false;
        if (remote.deleted) {
            getWritableDatabase().delete(T, "id = ?", new String[] { remote.id });
        } else {
            getWritableDatabase().insertWithOnConflict(T, null, toValues(remote), SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    /** Example of writing a file to app-private internal storage (files/ dir). */
    public File exportToInternalFile(String fileName, String content) throws IOException {
        File out = new File(appContext.getFilesDir(), fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private List<Item> query(String where, String[] args, String orderBy) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T, null, where, args, null, null, orderBy)) {
            while (c.moveToNext()) {
                Item i = new Item();
                i.id = c.getString(c.getColumnIndexOrThrow("id"));
                i.title = c.getString(c.getColumnIndexOrThrow("title"));
                i.notes = c.getString(c.getColumnIndexOrThrow("notes"));
                i.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
                i.version = c.getLong(c.getColumnIndexOrThrow("version"));
                i.dirty = c.getInt(c.getColumnIndexOrThrow("dirty")) == 1;
                i.deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) == 1;
                out.add(i);
            }
        }
        return out;
    }

    private static ContentValues toValues(Item i) {
        ContentValues v = new ContentValues();
        v.put("id", i.id);
        v.put("title", i.title);
        v.put("notes", i.notes);
        v.put("updated_at", i.updatedAt);
        v.put("version", i.version);
        v.put("dirty", i.dirty ? 1 : 0);
        v.put("deleted", i.deleted ? 1 : 0);
        return v;
    }

    /** Model. Keep field names aligned with the REST contract's JSON. */
    public static final class Item {
        public String id;
        public String title;
        public String notes;
        public long updatedAt;
        public long version;
        public boolean dirty;
        public boolean deleted;
    }
}
