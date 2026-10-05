package com.hdlee.pdfnote;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Tiny LAN server for device-to-device note sync (no cloud, no automatic sync): while the "다른 기기와 동기화" card is open, another
 * Everynote (the Windows app) on the same Wi-Fi can list the open documents, download / upload their notes (sidecar JSON) and the
 * referenced images / recordings. Every request needs the 6-digit code shown on the card. Plain HTTP, private networks only.
 */
final class DeviceSync {
    interface Host {
        /** Open documents as JSON text: [{"id":sha256 of the PDF,"title":..,"pages":n}]. */
        String listDocuments() throws Exception;
        /** Notes of the document as sidecar JSON, or null when no open document has this id. */
        String exportDocument(String id) throws Exception;
        /** Replaces the notes of the open document with the sidecar JSON; false when no open document has this id. */
        boolean importDocument(String id, String json) throws Exception;
        /** The file of an image / recording / video asset (may not exist yet), or null for an invalid name. */
        File assetFile(String name);
        void onActivity(String message);
    }

    static final Pattern ASSET = Pattern.compile("^[a-f0-9-]{36}\\.(png|m4a|mp4)$");
    private static final int MAX_JSON = 16 * 1024 * 1024;
    private static final long MAX_ASSET = 256L * 1024 * 1024;

    private final Host host;
    private final String code;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private int failures;

    DeviceSync(Host host) { this.host = host; this.code = String.format("%06d", new SecureRandom().nextInt(1000000)); }

    String code() { return code; }
    int port() { return server == null ? 0 : server.getLocalPort(); }

    void start() throws IOException {
        server = new ServerSocket(0);
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try { final Socket s = server.accept(); pool.execute(() -> handle(s)); }
                catch (IOException e) { if (!running) return; }
            }
        }, "device-sync");
        t.setDaemon(true); t.start();
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }

    /** The phone's address on the local network (Wi-Fi first), or null when not connected. */
    static String localAddress() {
        String found = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress a = addresses.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (ni.getName().startsWith("wlan")) return a.getHostAddress();
                        if (found == null) found = a.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) { }
        return found;
    }

    // ------------------------------------------------------------------------------------------------ HTTP
    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String line = readLine(in);
            if (line == null) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) { reply(out, 400, "text/plain", bytes("bad request")); return; }
            String method = parts[0], path = parts[1];
            long length = 0; String given = "";
            for (String h; (h = readLine(in)) != null && !h.isEmpty(); ) {
                int c = h.indexOf(':'); if (c < 0) continue;
                String name = h.substring(0, c).trim().toLowerCase(java.util.Locale.ROOT), value = h.substring(c + 1).trim();
                if (name.equals("content-length")) { try { length = Long.parseLong(value); } catch (NumberFormatException e) { length = -1; } }
                else if (name.equals("x-code")) given = value;
            }
            if (length < 0) { reply(out, 400, "text/plain", bytes("bad length")); return; }
            synchronized (this) {
                if (failures >= 10) { reply(out, 429, "text/plain", bytes("too many wrong codes")); return; }
                if (!code.equals(given)) { failures++; reply(out, 403, "text/plain", bytes("wrong code")); return; }
            }
            route(method, path, length, in, out);
        } catch (Exception ignored) { }
    }

    private void route(String method, String path, long length, InputStream in, OutputStream out) throws Exception {
        int q = path.indexOf('?'); if (q >= 0) path = path.substring(0, q);
        if (method.equals("GET") && path.equals("/v1/docs")) { reply(out, 200, "application/json", bytes(host.listDocuments())); return; }
        if (path.startsWith("/v1/doc/")) {
            String id = path.substring(8);
            if (!id.matches("[a-f0-9]{64}")) { reply(out, 400, "text/plain", bytes("bad id")); return; }
            if (method.equals("GET")) {
                String json = host.exportDocument(id);
                if (json == null) reply(out, 404, "text/plain", bytes("document not open")); else reply(out, 200, "application/json", bytes(json));
                return;
            }
            if (method.equals("PUT")) {
                if (length > MAX_JSON) { reply(out, 413, "text/plain", bytes("too large")); return; }
                String json = new String(readBody(in, (int) length), StandardCharsets.UTF_8);
                if (host.importDocument(id, json)) { host.onActivity("받음"); reply(out, 200, "text/plain", bytes("ok")); } else reply(out, 404, "text/plain", bytes("document not open"));
                return;
            }
        }
        if (path.startsWith("/v1/asset/")) {
            String name = path.substring(10);
            if (!ASSET.matcher(name).matches()) { reply(out, 400, "text/plain", bytes("bad name")); return; }
            File file = host.assetFile(name);
            if (file == null) { reply(out, 400, "text/plain", bytes("bad name")); return; }
            if (method.equals("GET") || method.equals("HEAD")) {
                if (!file.isFile()) { reply(out, 404, "text/plain", bytes("missing")); return; }
                writeHead(out, 200, "application/octet-stream", file.length());
                if (method.equals("GET")) try (InputStream f = new FileInputStream(file)) { byte[] b = new byte[16384]; int n; while ((n = f.read(b)) > 0) out.write(b, 0, n); }
                out.flush(); return;
            }
            if (method.equals("PUT")) {
                if (length > MAX_ASSET) { reply(out, 413, "text/plain", bytes("too large")); return; }
                File dir = file.getParentFile(); if (dir != null) dir.mkdirs();
                File tmp = new File(dir, name + ".part");
                try (OutputStream f = new FileOutputStream(tmp)) {
                    byte[] b = new byte[16384]; long left = length;
                    while (left > 0) { int n = in.read(b, 0, (int) Math.min(b.length, left)); if (n < 0) throw new EOFException(); f.write(b, 0, n); left -= n; }
                }
                if (!file.exists() && !tmp.renameTo(file)) { tmp.delete(); throw new IOException("rename"); }
                tmp.delete();
                host.onActivity("받음");
                reply(out, 200, "text/plain", bytes("ok")); return;
            }
        }
        reply(out, 404, "text/plain", bytes("not found"));
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static byte[] readBody(InputStream in, int length) throws IOException {
        byte[] b = new byte[length]; int off = 0;
        while (off < length) { int n = in.read(b, off, length - off); if (n < 0) throw new EOFException(); off += n; }
        return b;
    }
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int c;
        while ((c = in.read()) != -1) { if (c == '\n') break; if (c != '\r') sb.append((char) c); if (sb.length() > 8192) throw new IOException("line"); }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }
    private static void writeHead(OutputStream out, int status, String type, long length) throws IOException {
        String text = status == 200 ? "OK" : status == 400 ? "Bad Request" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 413 ? "Payload Too Large" : "Error";
        out.write(bytes("HTTP/1.1 " + status + " " + text + "\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + length + "\r\nConnection: close\r\n\r\n"));
    }
    private static void reply(OutputStream out, int status, String type, byte[] body) throws IOException {
        writeHead(out, status, type, body.length); out.write(body); out.flush();
    }
}
