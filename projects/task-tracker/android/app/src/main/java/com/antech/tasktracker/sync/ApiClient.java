package com.antech.tasktracker.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.antech.tasktracker.BuildConfig;

/**
 * Minimal JSON client on HttpURLConnection (no third-party libraries). Blocking: call off the main thread.
 * The base URL is kept in private SharedPreferences (internal storage) and defaults per build type.
 */
public final class ApiClient {

    private static final String PREFS = "settings";
    private static final String KEY_BASE_URL = "api_base_url";
    private static final int TIMEOUT_MS = 10_000;

    private final SharedPreferences prefs;

    public ApiClient(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String baseUrl() {
        return prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_API_BASE);
    }

    /** @return false when the URL is not acceptable (release builds require https). */
    public boolean setBaseUrl(String url) {
        String clean = url == null ? "" : url.trim().replaceAll("/+$", "");
        boolean ok = clean.startsWith("https://") || (BuildConfig.DEBUG && clean.startsWith("http://"));
        if (ok) prefs.edit().putString(KEY_BASE_URL, clean).apply();
        return ok;
    }

    public JSONObject get(String path) throws IOException, ApiException {
        return send("GET", path, null);
    }

    public JSONObject put(String path, JSONObject body) throws IOException, ApiException {
        return send("PUT", path, body);
    }

    public JSONObject delete(String path) throws IOException, ApiException {
        return send("DELETE", path, null);
    }

    private JSONObject send(String method, String path, JSONObject body) throws IOException, ApiException {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = in == null ? "" : readAll(in);
            if (status >= 400) throw new ApiException(status, detailOf(text));
            if (text.isEmpty()) return new JSONObject();
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new IOException("Invalid JSON from server", e);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String detailOf(String problemJson) {
        try {
            return new JSONObject(problemJson).optString("detail", "Request failed");
        } catch (JSONException e) {
            return "Request failed";
        }
    }

    public static final class ApiException extends Exception {
        public final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
