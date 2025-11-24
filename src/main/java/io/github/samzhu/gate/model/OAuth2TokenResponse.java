package io.github.samzhu.gate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OAuth2 token response from token endpoint.
 * Maps the standard OAuth2 token response format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("scope")
    private String scope;

    /**
     * Calculated expiration time (not from response)
     */
    private Instant expiresAt;

    /**
     * Creates token response from access token and expiration time
     */
    public OAuth2TokenResponse(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresAt = expiresAt;
    }

    /**
     * Checks if the token is expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * Checks if the token is valid (not expired)
     */
    public boolean isValid() {
        return !isExpired();
    }

    /**
     * Gets formatted bearer token string
     */
    public String getBearerToken() {
        return "Bearer " + accessToken;
    }
}
