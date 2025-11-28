package io.github.samzhu.gate.model;

/**
 * OAuth2 authorization callback result.
 * Contains the authorization code and state from the callback.
 *
 * @param code  The authorization code returned by the authorization server
 * @param state The state parameter for CSRF protection verification
 */
public record AuthorizationResult(String code, String state) {}
