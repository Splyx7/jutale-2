package de.spotifyglass;

import com.google.gson.*;
import net.minecraft.util.Util;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Spotify Web API: Login (PKCE), Status-Polling und Steuerung. */
public class Spotify {
    public static final Spotify I = new Spotify();
    static final String API = "https://api.spotify.com/v1";
    static final String REDIRECT = "http://127.0.0.1:8888/callback";
    static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";

    public volatile String title = "Nicht verbunden", artist = "K drücken → Mehr → Verbinden", status = "";
    public volatile boolean playing;
    public volatile int volume = 50;
    public volatile long duration, progress, stamp;
    volatile String artistId, albumImg, imgKey;
    volatile long pauseUntil, lastVolSend;
    String access;
    long accessExp;

    final HttpClient http = HttpClient.newHttpClient();
    final ScheduledExecutorService ex = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "spotifyglass"); t.setDaemon(true); return t; });

    public void start() { ex.scheduleWithFixedDelay(this::poll, 1, 2, TimeUnit.SECONDS); }

    public long progressNow() {
        if (!playing) return progress;
        return Math.min(duration, progress + (System.currentTimeMillis() - stamp));
    }

    // ---------- Polling ----------
    void poll() {
        try {
            if (System.currentTimeMillis() < pauseUntil) return;
            if (Cfg.I.refreshToken.isEmpty()) { title = "Nicht verbunden"; artist = "K drücken → Mehr → Verbinden"; return; }
            ensureToken();
            HttpResponse<String> r = get("/me/player");
            int sc = r.statusCode();
            if (sc == 204) { playing = false; title = "Kein aktives Gerät"; artist = "Starte Spotify irgendwo"; return; }
            if (sc == 401) { access = null; return; }
            if (sc == 429) { pauseUntil = System.currentTimeMillis() + 1000L * r.headers().firstValueAsLong("Retry-After").orElse(5); return; }
            if (sc != 200) { status = "API-Fehler " + sc; return; }
            JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
            playing = j.has("is_playing") && j.get("is_playing").getAsBoolean();
            progress = getl(j, "progress_ms", 0);
            stamp = System.currentTimeMillis();
            if (j.has("device") && j.get("device").isJsonObject() && System.currentTimeMillis() - lastVolSend > 1500)
                volume = geti(j.getAsJsonObject("device"), "volume_percent", volume);
            if (j.has("item") && j.get("item").isJsonObject()) {
                JsonObject it = j.getAsJsonObject("item");
                title = gets(it, "name", "?");
                duration = getl(it, "duration_ms", 0);
                JsonArray ar = it.has("artists") ? it.getAsJsonArray("artists") : new JsonArray();
                if (ar.size() > 0) {
                    JsonObject a0 = ar.get(0).getAsJsonObject();
                    artistId = gets(a0, "id", null);
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement e : ar) { if (sb.length() > 0) sb.append(", "); sb.append(gets(e.getAsJsonObject(), "name", "")); }
                    artist = sb.toString();
                }
                albumImg = pickImage(it.has("album") ? it.getAsJsonObject("album") : null);
                String key = Cfg.I.artistIcon ? "a:" + artistId : "c:" + albumImg;
                if (!key.equals(imgKey)) {
                    imgKey = key;
                    String url = Cfg.I.artistIcon && artistId != null ? artistImage(artistId) : albumImg;
                    Covers.load(url);
                }
            }
            status = "Verbunden";
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            status = m.length() > 40 ? m.substring(0, 40) : m;
        }
    }

    String pickImage(JsonObject o) {
        if (o == null || !o.has("images")) return null;
        JsonArray a = o.getAsJsonArray("images");
        if (a.size() == 0) return null;
        return gets(a.get(Math.min(1, a.size() - 1)).getAsJsonObject(), "url", null);
    }

    String artistImage(String id) {
        try {
            HttpResponse<String> r = get("/artists/" + id);
            if (r.statusCode() == 200) {
                String u = pickImage(JsonParser.parseString(r.body()).getAsJsonObject());
                if (u != null) return u;
            }
        } catch (Exception ignored) { }
        return albumImg;
    }

    static int geti(JsonObject o, String k, int d) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : d; }
    static long getl(JsonObject o, String k, long d) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : d; }
    static String gets(JsonObject o, String k, String d) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d; }

    // ---------- Steuerung ----------
    public void togglePlay() {
        boolean p = !playing;
        progress = progressNow(); stamp = System.currentTimeMillis(); playing = p;
        cmd("PUT", p ? "/me/player/play" : "/me/player/pause");
    }
    public void next() { cmd("POST", "/me/player/next"); ex.schedule(this::poll, 700, TimeUnit.MILLISECONDS); }
    public void prev() { cmd("POST", "/me/player/previous"); ex.schedule(this::poll, 700, TimeUnit.MILLISECONDS); }
    public void setVolume(int v, boolean force) {
        v = Math.max(0, Math.min(100, v));
        volume = v;
        long now = System.currentTimeMillis();
        if (force || now - lastVolSend > 250) { lastVolSend = now; cmd("PUT", "/me/player/volume?volume_percent=" + v); }
        else lastVolSend = Math.max(lastVolSend, now - 250 + 1);
    }
    public void seek(long ms) {
        progress = ms; stamp = System.currentTimeMillis();
        cmd("PUT", "/me/player/seek?position_ms=" + ms);
    }

    void cmd(String method, String path) {
        ex.submit(() -> {
            try {
                ensureToken();
                http.send(HttpRequest.newBuilder(URI.create(API + path))
                        .header("Authorization", "Bearer " + access)
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) { }
        });
    }

    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(API + path))
                .header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---------- Auth (PKCE, ohne Client Secret) ----------
    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    synchronized void ensureToken() throws Exception {
        if (access != null && System.currentTimeMillis() < accessExp - 30000) return;
        JsonObject j = tokenPost("grant_type=refresh_token&refresh_token=" + enc(Cfg.I.refreshToken) + "&client_id=" + enc(Cfg.I.clientId));
        access = j.get("access_token").getAsString();
        accessExp = System.currentTimeMillis() + j.get("expires_in").getAsLong() * 1000L;
        if (j.has("refresh_token")) { Cfg.I.refreshToken = j.get("refresh_token").getAsString(); Cfg.save(); }
    }

    JsonObject tokenPost(String body) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            if (r.body().contains("invalid_grant")) { Cfg.I.refreshToken = ""; Cfg.save(); }
            throw new IOException("Login-Fehler " + r.statusCode());
        }
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    public void login() {
        if (Cfg.I.clientId.isBlank()) { status = "Erst Client ID eintragen"; return; }
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(8888, 5, InetAddress.getByName("127.0.0.1"))) {
                ss.setSoTimeout(180_000);
                SecureRandom rnd = new SecureRandom();
                byte[] vb = new byte[48]; rnd.nextBytes(vb);
                String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(vb);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                String state = Long.toHexString(rnd.nextLong());
                String url = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(Cfg.I.clientId)
                        + "&scope=" + enc(SCOPES) + "&redirect_uri=" + enc(REDIRECT) + "&state=" + state
                        + "&code_challenge_method=S256&code_challenge=" + challenge;
                status = "Warte auf Browser …";
                Util.getOperatingSystem().open(URI.create(url));
                String code = null;
                for (int i = 0; i < 5 && code == null; i++) {
                    try (Socket s = ss.accept()) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                        String line = br.readLine();
                        Map<String, String> q = new HashMap<>();
                        if (line != null && line.split(" ").length > 1) {
                            String raw = URI.create("http://x" + line.split(" ")[1]).getRawQuery();
                            if (raw != null) for (String kv : raw.split("&")) {
                                String[] p = kv.split("=", 2);
                                if (p.length == 2) q.put(p[0], URLDecoder.decode(p[1], StandardCharsets.UTF_8));
                            }
                        }
                        boolean ok = q.containsKey("code") && state.equals(q.get("state"));
                        String html = "<html><body style='font-family:sans-serif;text-align:center;margin-top:20vh'><h2>"
                                + (ok ? "Verbunden ✔ – du kannst dieses Fenster schließen." : "Warte …") + "</h2></body></html>";
                        byte[] hb = html.getBytes(StandardCharsets.UTF_8);
                        OutputStream os = s.getOutputStream();
                        os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: "
                                + hb.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        os.write(hb); os.flush();
                        if (ok) code = q.get("code");
                        else if (q.containsKey("error")) { status = "Abgebrochen"; return; }
                    }
                }
                if (code == null) { status = "Kein Code erhalten"; return; }
                JsonObject j = tokenPost("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                        + "&client_id=" + enc(Cfg.I.clientId) + "&code_verifier=" + enc(verifier));
                synchronized (this) {
                    access = j.get("access_token").getAsString();
                    accessExp = System.currentTimeMillis() + j.get("expires_in").getAsLong() * 1000L;
                }
                Cfg.I.refreshToken = j.get("refresh_token").getAsString();
                Cfg.save();
                status = "Verbunden";
                poll();
            } catch (Exception e) {
                status = "Login fehlgeschlagen";
            }
        }, "spotifyglass-login");
        t.setDaemon(true);
        t.start();
    }
}
