package __APP_ID__.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import __APP_ID__.data.LocalStore;
import __APP_ID__.data.LocalStore.Item;

/**
 * Best-effort two-way sync. Local data in internal storage is never lost if the network fails:
 * dirty rows stay dirty and are retried next time.
 */
public final class SyncWorker {

    public interface Callback { void onDone(boolean ok, String message); }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String RESOURCE = "/api/v1/items";

    private final LocalStore store;
    private final ApiClient api;
    private final SharedPreferences prefs;

    public SyncWorker(Context context) {
        this.store = LocalStore.get(context);
        this.api = new ApiClient(context);
        this.prefs = context.getApplicationContext().getSharedPreferences("sync", Context.MODE_PRIVATE);
    }

    /** Callback runs on the background thread; wrap UI updates in runOnUiThread. */
    public void syncNow(Callback cb) {
        IO.execute(() -> {
            try {
                push();
                pull();
                cb.onDone(true, "Synced");
            } catch (IOException e) {
                cb.onDone(false, "Offline - changes saved on this device");
            } catch (ApiClient.ApiException | JSONException e) {
                cb.onDone(false, "Sync failed: " + e.getMessage());
            }
        });
    }

    private void push() throws IOException, ApiClient.ApiException, JSONException {
        for (Item item : store.dirtyRows()) {
            String path = RESOURCE + "/" + item.id;
            if (item.deleted) {
                try {
                    api.delete(path);
                } catch (ApiClient.ApiException e) {
                    if (e.status != 404) throw e;
                }
                store.markSynced(item.id, item.version);
                continue;
            }
            JSONObject body = new JSONObject()
                    .put("title", item.title)
                    .put("notes", item.notes == null ? JSONObject.NULL : item.notes)
                    .put("version", item.version);
            try {
                JSONObject saved = api.put(path, body);
                store.markSynced(item.id, saved.optLong("version", item.version));
            } catch (ApiClient.ApiException e) {
                if (e.status != 409) throw e;
                // Conflict: server wins. Fetch the server copy and replace the local row.
                JSONObject server = api.get(path);
                item.dirty = false;
                store.markSynced(item.id, server.optLong("version"));
                store.applyRemote(fromJson(server));
            }
        }
    }

    private void pull() throws IOException, ApiClient.ApiException, JSONException {
        String since = prefs.getString("last_pull", "1970-01-01T00:00:00Z");
        String startedAt = Instant.now().toString();
        JSONObject res = api.get(RESOURCE + "/changes?since=" + URLEncoder.encode(since, "UTF-8"));
        JSONArray content = res.optJSONArray("content");
        if (content != null) {
            for (int i = 0; i < content.length(); i++) store.applyRemote(fromJson(content.getJSONObject(i)));
        }
        prefs.edit().putString("last_pull", startedAt).apply();
    }

    static Item fromJson(JSONObject o) {
        Item i = new Item();
        i.id = o.optString("id");
        i.title = o.optString("title");
        i.notes = o.isNull("notes") ? null : o.optString("notes");
        i.version = o.optLong("version");
        i.deleted = o.optBoolean("deleted", false);
        i.updatedAt = parseMillis(o.optString("updatedAt", null));
        return i;
    }

    private static long parseMillis(String iso) {
        try {
            return iso == null ? System.currentTimeMillis() : Instant.parse(iso).toEpochMilli();
        } catch (RuntimeException e) {
            return System.currentTimeMillis();
        }
    }
}
