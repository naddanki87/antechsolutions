package com.antech.tasktracker.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.antech.tasktracker.data.Task;
import com.antech.tasktracker.data.TaskStore;

/**
 * Best-effort two-way sync. Local data in internal storage is never lost when the network fails:
 * dirty rows stay dirty and are retried on the next sync.
 */
public final class SyncWorker {

    public interface Callback {
        /** Runs on the sync thread; wrap UI work in runOnUiThread. */
        void onDone(boolean ok, String message);
    }

    private static final ExecutorService SYNC = Executors.newSingleThreadExecutor();
    private static final String RESOURCE = "/api/v1/tasks";
    private static final String KEY_SINCE = "last_server_change";
    private static final int PAGE_LIMIT = 500;

    private final TaskStore store;
    private final ApiClient api;
    private final SharedPreferences prefs;

    public SyncWorker(Context context) {
        this.store = TaskStore.get(context);
        this.api = new ApiClient(context);
        this.prefs = context.getApplicationContext().getSharedPreferences("sync", Context.MODE_PRIVATE);
    }

    public void syncNow(Callback cb) {
        SYNC.execute(() -> {
            try {
                int pushed = push();
                int pulled = pull();
                cb.onDone(true, "Synced: " + pushed + " sent, " + pulled + " received");
            } catch (IOException e) {
                cb.onDone(false, "Offline - changes are saved on this device");
            } catch (ApiClient.ApiException | JSONException e) {
                cb.onDone(false, "Sync failed: " + e.getMessage());
            }
        });
    }

    private int push() throws IOException, ApiClient.ApiException, JSONException {
        int count = 0;
        for (Task task : store.dirtyRows()) {
            String path = RESOURCE + "/" + task.id;
            if (task.deleted) {
                try {
                    api.delete(path);
                } catch (ApiClient.ApiException e) {
                    if (e.status != 404) throw e;
                }
                store.markSynced(task, 0);
            } else {
                try {
                    JSONObject saved = api.put(path, TaskJson.toUpsertBody(task));
                    store.markSynced(task, saved.optLong("version", 0));
                } catch (ApiClient.ApiException e) {
                    if (e.status != 409) throw e;
                    resolveConflict(path, task);
                }
            }
            count++;
        }
        return count;
    }

    /** 409: the server copy wins. If the server deleted the task, it is removed locally too. */
    private void resolveConflict(String path, Task task) throws IOException, ApiClient.ApiException {
        try {
            store.applyRemote(TaskJson.fromJson(api.get(path)), true);
        } catch (ApiClient.ApiException e) {
            if (e.status != 404) throw e;
            store.removeLocal(task.id);
        }
    }

    /** Pulls server changes since the last server timestamp seen (server clock, not the device clock). */
    private int pull() throws IOException, ApiClient.ApiException, JSONException {
        int count = 0;
        while (true) {
            String since = prefs.getString(KEY_SINCE, "1970-01-01T00:00:00Z");
            JSONObject res = api.get(RESOURCE + "/changes?since=" + URLEncoder.encode(since, "UTF-8"));
            JSONArray content = res.optJSONArray("content");
            if (content == null || content.length() == 0) return count;
            String latest = since;
            for (int i = 0; i < content.length(); i++) {
                JSONObject o = content.getJSONObject(i);
                store.applyRemote(TaskJson.fromJson(o), false);
                latest = o.optString("updatedAt", latest);
                count++;
            }
            prefs.edit().putString(KEY_SINCE, latest).apply();
            if (content.length() < PAGE_LIMIT) return count;
        }
    }
}
