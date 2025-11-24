package io.github.samzhu.gate.exception;

/**
 * Exception thrown when OAuth2 operations fail.
 */
public class OAuth2Exception extends RuntimeException {

    public OAuth2Exception(String message) {
        super(message);
    }

    public OAuth2Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public static OAuth2Exception authenticationFailed(String reason) {
        return new OAuth2Exception("OAuth2 authentication failed: " + reason);
    }

    public static OAuth2Exception invalidTokenResponse(String reason) {
        return new OAuth2Exception("Invalid token response: " + reason);
    }

    public static OAuth2Exception connectionFailed(String tokenUrl, Throwable cause) {
        return new OAuth2Exception("Failed to connect to token URL: " + tokenUrl, cause);
    }

    public static OAuth2Exception tokenExpired() {
        return new OAuth2Exception("OAuth2 token has expired");
    }
}
