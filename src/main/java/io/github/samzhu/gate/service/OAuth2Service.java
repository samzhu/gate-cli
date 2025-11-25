package io.github.samzhu.gate.service;

import io.github.samzhu.gate.exception.OAuth2Exception;
import io.github.samzhu.gate.model.OAuth2TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Service for OAuth2 client credentials flow.
 * Handles token acquisition from OAuth2 token endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final RestClient.Builder restClientBuilder;

    /**
     * Obtains an access token using OAuth2 client credentials flow.
     *
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @param tokenUrl     OAuth2 token endpoint URL
     * @return OAuth2TokenResponse containing access token and expiration
     * @throws OAuth2Exception if authentication fails
     */
    public OAuth2TokenResponse getAccessToken(String clientId, String clientSecret, String tokenUrl) {
        try {
            log.debug("Requesting OAuth2 token from: {}", tokenUrl);

            // Warn if using HTTP instead of HTTPS
            if (tokenUrl.startsWith("http://")) {
                log.warn("⚠ Using HTTP for token URL. Consider using HTTPS for security.");
            }

            // Build HTTP Basic Authentication header (RFC 6749 Section 2.3.1)
            String credentials = clientId + ":" + clientSecret;
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            // Build request body (only grant_type, credentials in header)
            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "client_credentials");

            // Create RestClient for this request
            RestClient restClient = restClientBuilder
                    .baseUrl(tokenUrl)
                    .build();

            // Execute token request with Basic Authentication
            OAuth2TokenResponse response = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", "Basic " + encodedCredentials)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, responseEntity) -> {
                        String errorBody = new String(responseEntity.getBody().readAllBytes());
                        log.error("OAuth2 authentication failed (4xx): {}", errorBody);
                        throw OAuth2Exception.authenticationFailed("Client authentication failed. Check client ID and secret.");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, responseEntity) -> {
                        log.error("OAuth2 server error (5xx)");
                        throw OAuth2Exception.authenticationFailed("OAuth2 server error. Please try again later.");
                    })
                    .body(OAuth2TokenResponse.class);

            if (response == null || response.getAccessToken() == null) {
                throw OAuth2Exception.invalidTokenResponse("No access token in response");
            }

            // Calculate expiration time
            if (response.getExpiresIn() != null) {
                response.setExpiresAt(Instant.now().plus(Duration.ofSeconds(response.getExpiresIn())));
            }

            // Log success (only first 10 chars of token for security)
            String tokenPreview = response.getAccessToken().length() > 10 ?
                    response.getAccessToken().substring(0, 10) + "..." :
                    "***";
            log.info("✓ Successfully obtained OAuth2 token: {}...", tokenPreview);
            log.debug("Token expires at: {}", response.getExpiresAt());

            return response;
        } catch (OAuth2Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to obtain OAuth2 token", e);
            throw OAuth2Exception.connectionFailed(tokenUrl, e);
        }
    }

    /**
     * Validates an OAuth2 token.
     *
     * @param token Token to validate
     * @return true if token is valid (not expired)
     */
    public boolean validateToken(OAuth2TokenResponse token) {
        if (token == null) {
            return false;
        }

        boolean valid = token.isValid();
        if (!valid) {
            log.debug("Token validation failed: token is expired");
        }

        return valid;
    }

    /**
     * Checks token expiration and returns time until expiration.
     *
     * @param expiresAt Token expiration time
     * @return Duration until expiration, or null if already expired
     */
    public Duration getTimeUntilExpiration(Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }

        Instant now = Instant.now();
        if (expiresAt.isBefore(now)) {
            return null; // Already expired
        }

        return Duration.between(now, expiresAt);
    }

    /**
     * Formats duration in a human-readable format.
     *
     * @param duration Duration to format
     * @return Formatted string (e.g., "55 minutes", "2 hours")
     */
    public String formatDuration(Duration duration) {
        if (duration == null) {
            return "expired";
        }

        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        }

        long days = hours / 24;
        return days + " day" + (days != 1 ? "s" : "");
    }

    /**
     * Validates URL format.
     *
     * @param url URL to validate
     * @return true if URL is valid
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
