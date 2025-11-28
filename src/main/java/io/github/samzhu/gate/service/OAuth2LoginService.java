package io.github.samzhu.gate.service;

import io.github.samzhu.gate.exception.OAuth2Exception;
import io.github.samzhu.gate.model.AuthorizationResult;
import io.github.samzhu.gate.model.OIDCConfiguration;
import io.github.samzhu.gate.model.OAuth2TokenResponse;
import io.github.samzhu.gate.model.PKCEPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Service for OAuth2 Authorization Code Flow with PKCE.
 * Orchestrates the complete browser-based login flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2LoginService {

    private final OIDCDiscoveryService discoveryService;
    private final PKCEService pkceService;
    private final OAuthCallbackServer callbackServer;
    private final RestClient.Builder restClientBuilder;

    private static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Executes OAuth2 Authorization Code Flow with PKCE.
     *
     * @param issuerUri   The OAuth2 issuer URI
     * @param clientId    The OAuth2 client ID
     * @param scope       The OAuth2 scope
     * @param redirectUri The redirect URI for callbacks
     * @return OAuth2TokenResponse containing the access token
     * @throws OAuth2Exception if login fails
     */
    public OAuth2TokenResponse login(
            String issuerUri, String clientId, String scope, String redirectUri) {

        // 1. OIDC Discovery
        log.info("→ Discovering OIDC configuration...");
        OIDCConfiguration oidcConfig = discoveryService.discover(issuerUri);

        // 2. Generate PKCE pair and state
        PKCEPair pkce = pkceService.generate();
        String state = pkceService.generateState();

        // 3. Build authorization URL
        String authUrl = buildAuthorizationUrl(
                oidcConfig.getAuthorizationEndpoint(),
                clientId, redirectUri,
                pkce.codeChallenge(), state, scope
        );
        log.info("Authorization URL: {}", authUrl);

        // 4. Parse port from redirect URI
        int port = parsePort(redirectUri);

        // 5. Start callback server
        CompletableFuture<AuthorizationResult> callbackFuture =
                callbackServer.startAndWait(port, state, CALLBACK_TIMEOUT);

        // 6. Open browser
        log.info("→ Opening browser for authorization...");
        openBrowser(authUrl);

        // 7. Wait for callback
        log.info("→ Waiting for authorization (timeout: {} minutes)...", CALLBACK_TIMEOUT.toMinutes());

        AuthorizationResult authResult;
        try {
            authResult = callbackFuture.get();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new OAuth2Exception("Authorization failed: " + cause.getMessage(), cause);
        }

        // 8. Exchange authorization code for token
        log.info("→ Exchanging authorization code for token...");
        return exchangeToken(
                oidcConfig.getTokenEndpoint(),
                clientId, authResult.code(),
                redirectUri, pkce.codeVerifier()
        );
    }

    private String buildAuthorizationUrl(
            String authEndpoint, String clientId, String redirectUri,
            String codeChallenge, String state, String scope) {

        return UriComponentsBuilder.fromUriString(authEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", state)
                .queryParam("scope", scope)
                .build()
                .toUriString();
    }

    private OAuth2TokenResponse exchangeToken(
            String tokenEndpoint, String clientId, String code,
            String redirectUri, String codeVerifier) {

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("code_verifier", codeVerifier);

        try {
            RestClient restClient = restClientBuilder.build();

            OAuth2TokenResponse response = restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(OAuth2TokenResponse.class);

            if (response != null && response.getExpiresIn() != null) {
                response.setExpiresAt(Instant.now().plus(Duration.ofSeconds(response.getExpiresIn())));
            }

            return response;
        } catch (RestClientException e) {
            throw new OAuth2Exception("Failed to exchange authorization code for token", e);
        }
    }

    private void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
        } catch (Exception e) {
            log.error("Could not open browser automatically");
            throw new OAuth2Exception("Could not open browser. Please open this URL manually:\n" + url, e);
        }
    }

    private int parsePort(String redirectUri) {
        try {
            int port = new URI(redirectUri).getPort();
            return port > 0 ? port : 8080;
        } catch (Exception e) {
            return 8080;
        }
    }
}
