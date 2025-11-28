package io.github.samzhu.gate.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.samzhu.gate.exception.OAuth2Exception;
import io.github.samzhu.gate.model.AuthorizationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * OAuth callback server using JDK HttpServer.
 * Handles the OAuth2 authorization callback on localhost.
 */
@Slf4j
@Service
public class OAuthCallbackServer {

    private static final String SUCCESS_HTML = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Gate-CLI</title></head>
            <body style="font-family: system-ui, sans-serif; text-align: center; padding: 50px;">
                <h1 style="color: #22c55e;">✓ Authorization Successful</h1>
                <p>You can close this window and return to the terminal.</p>
            </body>
            </html>
            """;

    private static final String ERROR_HTML = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"><title>Gate-CLI</title></head>
            <body style="font-family: system-ui, sans-serif; text-align: center; padding: 50px;">
                <h1 style="color: #ef4444;">✗ Authorization Failed</h1>
                <p>%s</p>
            </body>
            </html>
            """;

    /**
     * Starts the callback server and waits for the authorization callback.
     *
     * @param port          The port to listen on
     * @param expectedState The expected state parameter for CSRF verification
     * @param timeout       Maximum time to wait for the callback
     * @return CompletableFuture containing the authorization result
     */
    public CompletableFuture<AuthorizationResult> startAndWait(
            int port, String expectedState, Duration timeout) {

        CompletableFuture<AuthorizationResult> future = new CompletableFuture<>();

        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", port), 0);

            server.createContext("/callback", exchange -> {
                try {
                    handleCallback(exchange, expectedState, future);
                } finally {
                    // Stop server after handling callback
                    server.stop(0);
                }
            });

            server.setExecutor(null);
            server.start();
            log.info("OAuth callback server started on http://127.0.0.1:{}/callback", port);

            // Timeout handling
            future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((result, ex) -> {
                        server.stop(0);
                        if (ex != null && !future.isDone()) {
                            log.warn("Callback timeout, server stopped");
                        }
                    });

        } catch (IOException e) {
            future.completeExceptionally(
                    new OAuth2Exception("Failed to start callback server on port " + port, e));
        }

        return future;
    }

    private void handleCallback(HttpExchange exchange, String expectedState,
                                 CompletableFuture<AuthorizationResult> future) {
        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

            String code = params.get("code");
            String state = params.get("state");
            String error = params.get("error");

            if (error != null) {
                String desc = params.getOrDefault("error_description", error);
                sendResponse(exchange, 400, String.format(ERROR_HTML, desc));
                future.completeExceptionally(new OAuth2Exception("Authorization denied: " + desc));
            } else if (code != null && expectedState.equals(state)) {
                sendResponse(exchange, 200, SUCCESS_HTML);
                future.complete(new AuthorizationResult(code, state));
            } else if (code != null) {
                sendResponse(exchange, 400, String.format(ERROR_HTML, "Invalid state parameter"));
                future.completeExceptionally(
                        new OAuth2Exception("State mismatch - possible CSRF attack"));
            } else {
                sendResponse(exchange, 400, String.format(ERROR_HTML, "Missing authorization code"));
                future.completeExceptionally(new OAuth2Exception("Missing authorization code in callback"));
            }
        } catch (IOException e) {
            log.error("Error handling callback", e);
            future.completeExceptionally(e);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(arr -> arr.length == 2)
                .collect(Collectors.toMap(
                        arr -> URLDecoder.decode(arr[0], StandardCharsets.UTF_8),
                        arr -> URLDecoder.decode(arr[1], StandardCharsets.UTF_8)
                ));
    }
}
