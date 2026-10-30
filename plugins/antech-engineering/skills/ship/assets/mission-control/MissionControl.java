import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Antech Mission Control - live dashboard for the /ship agent team. Zero dependencies, JDK 17+.
 *
 * Serve the dashboard (binds to 127.0.0.1 only):
 *   java MissionControl.java --project projects/my-app [--port 7070]
 *
 * Log an agent event (appends one JSON line to <project>/.ship/events.jsonl):
 *   java MissionControl.java log --project projects/my-app --agent Armor --to Byte --phase 3 \
 *        --kind message --status working --text "SEC-1: sort param returns 500"
 *
 * Agents may also append JSON lines to the events file directly, or POST them to /api/events.
 */
public class MissionControl {

    private static final int MAX_EVENT_BYTES = 16 * 1024;

    private final Path project;
    private final Path events;
    private final Path web;
    private volatile String gitCache = "{}";
    private volatile long gitCacheAt;

    MissionControl(Path project, Path web) {
        this.project = project.toAbsolutePath().normalize();
        this.events = this.project.resolve(".ship").resolve("events.jsonl");
        this.web = web.toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws Exception {
        boolean logMode = args.length > 0 && args[0].equals("log");
        Map<String, String> opts = parseOptions(args, logMode ? 1 : 0);
        Path project = Path.of(opts.getOrDefault("project", "."));
        if (logMode) {
            appendEvent(project.resolve(".ship").resolve("events.jsonl"), buildEvent(opts));
            return;
        }
        int port = Integer.parseInt(opts.getOrDefault("port", "7070"));
        Path web = opts.containsKey("web") ? Path.of(opts.get("web")) : locateWebDir();
        if (!Files.isRegularFile(web.resolve("index.html"))) {
            System.err.println("Dashboard files not found in " + web.toAbsolutePath() + " (use --web <dir>)");
            System.exit(1);
        }
        new MissionControl(project, web).start(port);
    }

    void start(int port) throws IOException {
        Files.createDirectories(events.getParent());
        if (!Files.exists(events)) Files.createFile(events);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/api/git", ex -> sendJson(ex, git()));
        server.createContext("/api/system", ex -> sendJson(ex, system()));
        server.createContext("/api/info", ex -> sendJson(ex, "{\"project\":" + q(project.getFileName().toString())
                + ",\"path\":" + q(project.toString()) + "}"));
        server.createContext("/", this::handleStatic);
        server.start();
        System.out.println("Antech Mission Control: http://localhost:" + port + "  (project " + project + ")");
    }

    // ---------------------------------------------------------------- events (SSE + POST)

    private void handleEvents(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            handlePost(ex);
            return;
        }
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        long offset = 0;
        long lastBeat = System.currentTimeMillis();
        boolean sentReady = false;
        byte[] partial = new byte[0];
        try (OutputStream out = ex.getResponseBody()) {
            while (true) {
                long size = Files.size(events);
                if (size < offset) { // file was truncated: tell the client to start over
                    offset = 0;
                    partial = new byte[0];
                    out.write("event: reset\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8));
                }
                if (size > offset) {
                    byte[] chunk;
                    try (RandomAccessFile raf = new RandomAccessFile(events.toFile(), "r")) {
                        raf.seek(offset);
                        chunk = new byte[(int) Math.min(size - offset, 4 * 1024 * 1024)];
                        raf.readFully(chunk);
                    }
                    offset += chunk.length;
                    byte[] data = concat(partial, chunk);
                    int start = 0;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < data.length; i++) {
                        if (data[i] == '\n') {
                            String line = new String(data, start, i - start, StandardCharsets.UTF_8).trim();
                            if (line.startsWith("{")) sb.append("data: ").append(line).append("\n\n");
                            start = i + 1;
                        }
                    }
                    partial = java.util.Arrays.copyOfRange(data, start, data.length);
                    if (sb.length() > 0) out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (!sentReady) {
                    out.write("event: ready\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8));
                    sentReady = true;
                }
                if (System.currentTimeMillis() - lastBeat > 15000) {
                    out.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                    lastBeat = System.currentTimeMillis();
                }
                out.flush();
                Thread.sleep(400);
            }
        } catch (IOException | InterruptedException e) {
            // client disconnected
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.matches("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?")) {
            sendStatus(ex, 403);
            return;
        }
        byte[] body = readLimited(ex.getRequestBody(), MAX_EVENT_BYTES);
        String json = body == null ? "" : new String(body, StandardCharsets.UTF_8).replace('\r', ' ').replace('\n', ' ').trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            sendStatus(ex, 400);
            return;
        }
        if (!json.contains("\"ts\"")) json = "{\"ts\":" + q(Instant.now().toString()) + (json.length() > 2 ? "," : "") + json.substring(1);
        appendEvent(events, json);
        sendStatus(ex, 204);
    }

    static synchronized void appendEvent(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static String buildEvent(Map<String, String> o) {
        StringBuilder sb = new StringBuilder("{\"ts\":").append(q(Instant.now().toString()));
        sb.append(",\"agent\":").append(q(o.getOrDefault("agent", "System")));
        if (o.containsKey("to")) {
            sb.append(",\"to\":[");
            String[] to = o.get("to").split(",");
            for (int i = 0; i < to.length; i++) sb.append(i > 0 ? "," : "").append(q(to[i].trim()));
            sb.append(']');
        }
        if (o.containsKey("phase")) sb.append(",\"phase\":").append(Integer.parseInt(o.get("phase")));
        sb.append(",\"kind\":").append(q(o.getOrDefault("kind", "message")));
        if (o.containsKey("status")) sb.append(",\"status\":").append(q(o.get("status")));
        sb.append(",\"text\":").append(q(o.getOrDefault("text", "")));
        if (o.containsKey("data")) sb.append(",\"data\":").append(o.get("data"));
        return sb.append('}').toString();
    }

    // ---------------------------------------------------------------- git / GitHub

    private synchronized String git() {
        if (System.currentTimeMillis() - gitCacheAt < 10000) return gitCache;
        String branch = run("git", "-C", project.toString(), "rev-parse", "--abbrev-ref", "HEAD").trim();
        String remote = run("git", "-C", project.toString(), "remote", "get-url", "origin").trim();
        String log = run("git", "-C", project.toString(), "log", "-12", "--pretty=format:%h%x1f%s%x1f%cr%x1f%D");
        String dirty = run("git", "-C", project.toString(), "status", "--porcelain");
        String runs = run("gh", "run", "list", "--limit", "6", "--json",
                "status,conclusion,headBranch,displayTitle,workflowName,createdAt,url");
        StringBuilder sb = new StringBuilder("{\"branch\":").append(q(branch)).append(",\"remote\":").append(q(remote));
        sb.append(",\"uncommitted\":").append(dirty.isBlank() ? 0 : dirty.strip().split("\n").length);
        sb.append(",\"commits\":[");
        boolean first = true;
        for (String line : log.split("\n")) {
            String[] p = line.split("\u001f", -1);
            if (p.length < 4) continue;
            sb.append(first ? "" : ",").append("{\"sha\":").append(q(p[0])).append(",\"subject\":").append(q(p[1]))
                    .append(",\"when\":").append(q(p[2])).append(",\"refs\":").append(q(p[3])).append('}');
            first = false;
        }
        sb.append("],\"runs\":").append(runs.trim().startsWith("[") ? runs.trim() : "[]").append('}');
        gitCache = sb.toString();
        gitCacheAt = System.currentTimeMillis();
        return gitCache;
    }

    private String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).directory(project.toFile()).redirectErrorStream(false).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(15, TimeUnit.SECONDS)) p.destroyForcibly();
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- system metrics

    private String system() {
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long total = os.getTotalMemorySize();
        long free = os.getFreeMemorySize();
        long eventsSize = 0;
        try { eventsSize = Files.size(events); } catch (IOException ignored) { }
        return "{\"cpu\":" + Math.max(0, os.getCpuLoad()) + ",\"memUsed\":" + (total - free) + ",\"memTotal\":" + total
                + ",\"cores\":" + os.getAvailableProcessors() + ",\"eventsBytes\":" + eventsSize
                + ",\"ts\":" + q(Instant.now().toString()) + "}";
    }

    // ---------------------------------------------------------------- static files

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = URI.create(ex.getRequestURI().getPath()).getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = web.resolve(path.substring(1)).normalize();
        if (!file.startsWith(web) || !Files.isRegularFile(file)) {
            sendStatus(ex, 404);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------- helpers

    private static void securityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; frame-ancestors 'none'");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
    }

    private static void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendStatus(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > max) return null;
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** JSON string literal. */
    static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static Map<String, String> parseOptions(String[] args, int from) {
        Map<String, String> opts = new HashMap<>();
        for (int i = from; i < args.length; i++) {
            if (args[i].startsWith("--") && i + 1 < args.length) opts.put(args[i].substring(2), args[++i]);
        }
        return opts;
    }

    /** Finds the web/ folder next to this source file when started as `java path/MissionControl.java`. */
    private static Path locateWebDir() {
        String cmd = System.getProperty("sun.java.command", "");
        List<String> candidates = new ArrayList<>();
        for (String part : cmd.split(" ")) {
            if (part.endsWith("MissionControl.java")) candidates.add(part);
        }
        for (String c : candidates) {
            Path dir = Path.of(c).toAbsolutePath().getParent();
            if (dir != null && Files.isDirectory(dir.resolve("web"))) return dir.resolve("web");
        }
        return Path.of("web");
    }
}
