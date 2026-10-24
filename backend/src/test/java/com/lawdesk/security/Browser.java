package com.lawdesk.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A minimal browser for tests over real HTTP: keeps cookies (also {@code Secure} ones, which a real browser only
 * sends over HTTPS) and sends the CSRF header the SPA sends (architecture/CONTRACTS.md §3).
 */
public final class Browser {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final int port;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private HttpResponse<String> last;

    public Browser(int port) {
        this.port = port;
    }

    public HttpResponse<String> get(String path) {
        return send(request(path).GET());
    }

    public HttpResponse<String> post(String path, String json) {
        return send(withCsrf(request(path)).POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> put(String path, String json) {
        return send(withCsrf(request(path)).PUT(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> postWithoutCsrf(String path, String json) {
        return send(request(path).POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    /** Fetches the CSRF cookie the SPA loads first, then signs in. */
    public HttpResponse<String> login(String login, String password) {
        get("/api/v1/auth/csrf");
        return post("/api/v1/auth/login", "{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password));
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void cookie(String name, String value) {
        cookies.put(name, value);
    }

    /** The raw Set-Cookie headers of the last response, for attribute checks. */
    public List<String> setCookies() {
        return last.headers().allValues("Set-Cookie");
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Accept", "application/json");
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        return builder;
    }

    private HttpRequest.Builder withCsrf(HttpRequest.Builder builder) {
        String token = cookies.get("XSRF-TOKEN");
        return token == null ? builder : builder.header("X-XSRF-TOKEN", token);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            last = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        for (String header : last.headers().allValues("Set-Cookie")) {
            String pair = header.split(";", 2)[0];
            String name = pair.substring(0, pair.indexOf('='));
            String value = pair.substring(pair.indexOf('=') + 1);
            boolean expired = header.toLowerCase().contains("max-age=0") || value.isEmpty();
            if (expired) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
        return last;
    }
}
