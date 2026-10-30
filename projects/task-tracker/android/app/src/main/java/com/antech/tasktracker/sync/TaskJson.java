package com.antech.tasktracker.sync;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.antech.tasktracker.data.Task;

/** Maps between the local model and the REST contract's JSON. Pure Java so it is unit-testable. */
public final class TaskJson {

    private TaskJson() {
    }

    public static JSONObject toUpsertBody(Task t) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("title", t.title);
        o.put("notes", t.notes == null ? JSONObject.NULL : t.notes);
        o.put("dueDate", t.dueDate == null ? JSONObject.NULL : t.dueDate);
        o.put("priority", t.priority);
        o.put("done", t.done);
        if (t.version != null) o.put("version", t.version);
        return o;
    }

    public static Task fromJson(JSONObject o) {
        Task t = new Task();
        t.id = o.optString("id");
        t.title = o.optString("title");
        t.notes = o.isNull("notes") ? null : o.optString("notes");
        t.dueDate = o.isNull("dueDate") ? null : o.optString("dueDate");
        t.priority = o.optString("priority", "MEDIUM");
        t.done = o.optBoolean("done", false);
        t.deleted = o.optBoolean("deleted", false);
        t.version = o.has("version") && !o.isNull("version") ? o.optLong("version") : null;
        t.updatedAt = parseMillis(o.isNull("updatedAt") ? null : o.optString("updatedAt", null));
        t.dirty = false;
        return t;
    }

    static long parseMillis(String iso) {
        if (iso == null || iso.isEmpty()) return System.currentTimeMillis();
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException e) {
            return System.currentTimeMillis();
        }
    }
}
