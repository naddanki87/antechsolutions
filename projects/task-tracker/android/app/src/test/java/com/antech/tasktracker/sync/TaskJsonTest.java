package com.antech.tasktracker.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import com.antech.tasktracker.data.Task;

public class TaskJsonTest {

    @Test
    public void upsertBodyOmitsVersionForNeverSyncedTask() throws Exception {
        Task t = new Task();
        t.title = "Offline task";
        t.priority = "HIGH";
        JSONObject body = TaskJson.toUpsertBody(t);
        assertEquals("Offline task", body.getString("title"));
        assertEquals("HIGH", body.getString("priority"));
        assertTrue(body.isNull("notes"));
        assertTrue(body.isNull("dueDate"));
        assertFalse(body.has("version"));
    }

    @Test
    public void upsertBodyCarriesVersionForSyncedTask() throws Exception {
        Task t = new Task();
        t.title = "Synced";
        t.version = 4L;
        t.done = true;
        JSONObject body = TaskJson.toUpsertBody(t);
        assertEquals(4L, body.getLong("version"));
        assertTrue(body.getBoolean("done"));
    }

    @Test
    public void parsesServerTaskIncludingDeletedFlag() throws Exception {
        JSONObject o = new JSONObject("{\"id\":\"6f1c\",\"title\":\"Pay rent\",\"notes\":null,\"dueDate\":\"2026-11-01\","
                + "\"priority\":\"LOW\",\"done\":true,\"deleted\":true,\"version\":2,\"updatedAt\":\"2026-10-30T10:15:30Z\"}");
        Task t = TaskJson.fromJson(o);
        assertEquals("6f1c", t.id);
        assertNull(t.notes);
        assertEquals("2026-11-01", t.dueDate);
        assertEquals("LOW", t.priority);
        assertTrue(t.done);
        assertTrue(t.deleted);
        assertEquals(Long.valueOf(2), t.version);
        assertEquals(1793355330000L, t.updatedAt);
        assertFalse(t.dirty);
    }
}
