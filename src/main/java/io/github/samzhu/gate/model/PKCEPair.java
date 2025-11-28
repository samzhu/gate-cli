package io.github.samzhu.gate.model;

/**
 * PKCE code_verifier and code_challenge pair.
 * Used for OAuth 2.1 Authorization Code Flow with PKCE (RFC 7636).
 *
 * @param codeVerifier  The random code verifier (43 characters, Base64URL)
 * @param codeChallenge The SHA-256 hash of code_verifier (Base64URL encoded)
 */
public record PKCEPair(String codeVerifier, String codeChallenge) {}
