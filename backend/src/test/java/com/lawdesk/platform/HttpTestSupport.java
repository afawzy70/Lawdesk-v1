package com.lawdesk.platform;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Exercises the application over real HTTP so the whole servlet filter chain (correlation, security, error
 * handling) is part of every test.
 */
public abstract class HttpTestSupport {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    protected int port;

    protected HttpResponse<String> get(String path) {
        return get(path, Map.of());
    }

    protected HttpResponse<String> get(String path, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        headers.forEach(request::header);
        return send(request.build());
    }

    protected HttpResponse<String> post(String path, String body) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
