package io.github.samzhu.gate.service;

import io.github.samzhu.gate.model.PKCEPair;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for generating PKCE (Proof Key for Code Exchange) parameters.
 * Implements RFC 7636 for OAuth 2.0 Authorization Code Flow with PKCE.
 */
@Service
public class PKCEService {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a PKCE pair (code_verifier and code_challenge).
     *
     * @return PKCEPair containing the verifier and challenge
     */
    public PKCEPair generate() {
        // Generate code_verifier (43 characters, Base64URL encoded)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String codeVerifier = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        // Calculate code_challenge = BASE64URL(SHA256(code_verifier))
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String codeChallenge = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);

            return new PKCEPair(codeVerifier, codeChallenge);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generates a random state parameter for CSRF protection.
     *
     * @return Random state string (Base64URL encoded)
     */
    public String generateState() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }
}
