package com.atlauncher.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.atlauncher.data.ElyByAccount;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/** Public-client OAuth authorization code flow with PKCE and loopback redirect. */
public final class ElyByAuthAPI {
    public static final String CLIENT_ID = "et";
    public static final String REDIRECT_URI = System.getProperty("etlauncher.ely.redirectUri",
        "http://127.0.0.1:28563/callback");
    private static final String SCOPE = "account_info offline_access minecraft_server_session";
    private static final String TOKEN_URL = "https://account.ely.by/api/oauth2/v1/token";

    private ElyByAuthAPI() { }

    public static ElyByAccount login() throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] verifierBytes = new byte[48];
        byte[] stateBytes = new byte[24];
        random.nextBytes(verifierBytes);
        random.nextBytes(stateBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        java.net.URI redirect = new java.net.URI(REDIRECT_URI);
        if (!"http".equals(redirect.getScheme()) || !"127.0.0.1".equals(redirect.getHost())
            || redirect.getPort() < 1 || redirect.getRawQuery() != null || redirect.getFragment() != null) {
            throw new IOException("Ely.by redirect URI must be http://127.0.0.1:<port>/<path>");
        }
        ArrayBlockingQueue<Map<String, String>> callback = new ArrayBlockingQueue<>(1);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),
            redirect.getPort()), 0);
        server.createContext(redirect.getPath(), exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            byte[] response = "ETLauncher: вход завершён, вернитесь в лаунчер.".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(response); }
            callback.offer(params);
        });
        server.start();
        try {
            String uri = "https://account.ely.by/oauth2/v1?client_id=" + enc(CLIENT_ID)
                + "&redirect_uri=" + enc(REDIRECT_URI) + "&response_type=code&scope=" + enc(SCOPE)
                + "&state=" + enc(state) + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256";
            java.awt.Desktop.getDesktop().browse(new java.net.URI(uri));
            Map<String, String> result = callback.poll(120, TimeUnit.SECONDS);
            if (result == null) throw new IOException("Ely.by login timed out");
            if (!state.equals(result.get("state"))) throw new IOException("Invalid Ely.by OAuth state");
            if (result.containsKey("error")) throw new IOException("Ely.by: " + result.get("error"));
            if (!result.containsKey("code")) throw new IOException("Ely.by did not return a code");
            JsonObject token = post(TOKEN_URL, "client_id=" + enc(CLIENT_ID) + "&redirect_uri="
                + enc(REDIRECT_URI) + "&grant_type=authorization_code&code=" + enc(result.get("code"))
                + "&code_verifier=" + enc(verifier));
            return new ElyByAccount(token, getProfile(token.get("access_token").getAsString()));
        } finally { server.stop(0); }
    }

    public static JsonObject refresh(String refreshToken) throws IOException {
        if (refreshToken == null) throw new IOException("No Ely.by refresh token");
        return post(TOKEN_URL, "client_id=" + enc(CLIENT_ID) + "&grant_type=refresh_token&refresh_token="
            + enc(refreshToken) + "&scope=" + enc(SCOPE));
    }

    public static JsonObject getProfile(String accessToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://account.ely.by/api/account/v1/info")
            .openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        return read(conn);
    }

    /** Ely.by Minecraft authentication while their public-client OAuth page drops PKCE parameters. */
    public static ElyByAccount loginWithPassword(String username, String password, String totp) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("username", username);
        request.addProperty("password", totp == null || totp.isEmpty() ? password : password + ":" + totp);
        request.addProperty("clientToken", java.util.UUID.randomUUID().toString());
        request.addProperty("requestUser", true);
        return new ElyByAccount(postJson("https://authserver.ely.by/auth/authenticate", request));
    }

    public static JsonObject refreshLegacy(String accessToken, String clientToken) throws IOException {
        JsonObject request = new JsonObject();
        request.addProperty("accessToken", accessToken);
        request.addProperty("clientToken", clientToken);
        return postJson("https://authserver.ely.by/auth/refresh", request);
    }

    private static JsonObject postJson(String url, JsonObject body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) { out.write(data); }
        return read(conn);
    }

    private static JsonObject post(String url, String form) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        byte[] data = form.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) { out.write(data); }
        return read(conn);
    }

    private static JsonObject read(HttpURLConnection conn) throws IOException {
        try {
            int status = conn.getResponseCode();
            try (InputStream in = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                if (in == null) throw new IOException("HTTP " + status);
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] bytes = new byte[4096];
                for (int size; (size = in.read(bytes)) >= 0;) buffer.write(bytes, 0, size);
                String body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                if (status >= 400) throw new IOException("Ely.by HTTP " + status + ": " + body);
                return new JsonParser().parse(body).getAsJsonObject();
            }
        } finally { conn.disconnect(); }
    }

    private static Map<String, String> parseQuery(String query) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (query != null) for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], "UTF-8"),
                URLDecoder.decode(parts.length == 2 ? parts[1] : "", "UTF-8"));
        }
        return values;
    }

    private static String enc(String s) throws IOException { return URLEncoder.encode(s, "UTF-8"); }
}
