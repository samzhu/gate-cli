package io.github.samzhu.gate.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents Claude Code settings structure from ~/.claude/settings.json
 * Based on official documentation: https://docs.claude.ai/en/settings
 *
 * Key environment variables in 'env':
 * - ANTHROPIC_AUTH_TOKEN: Auth token (Claude Code auto-adds "Bearer " prefix)
 * - ANTHROPIC_BASE_URL: Custom API endpoint URL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeSettings {

    /**
     * Environment variables for Claude Code configuration.
     * Key variables:
     * - ANTHROPIC_AUTH_TOKEN: Auth token (auto-prefixed with "Bearer ")
     * - ANTHROPIC_BASE_URL: Custom API endpoint URL
     */
    @Builder.Default
    private Map<String, String> env = new HashMap<>();

    /**
     * Permissions configuration
     */
    private Permissions permissions;

    /**
     * API key helper for dynamic credential generation
     */
    private String apiKeyHelper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Permissions {
        @Builder.Default
        private List<String> allow = List.of("*");
        @Builder.Default
        private List<String> deny = List.of();
        @Builder.Default
        private List<String> ask = List.of();
    }

    /**
     * Sets auth token in environment variables.
     * Note: Claude Code automatically adds "Bearer " prefix to ANTHROPIC_AUTH_TOKEN,
     * so we should NOT include "Bearer " in the value.
     *
     * @param token Raw access token (without "Bearer " prefix)
     */
    public void setAuthToken(String token) {
        if (env == null) {
            env = new HashMap<>();
        }
        // Remove "Bearer " prefix if present (for backwards compatibility)
        String rawToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            rawToken = token.substring(7);
        }
        env.put("ANTHROPIC_AUTH_TOKEN", rawToken);
    }

    /**
     * Sets custom API base URL in environment variables.
     *
     * @param baseUrl Custom API endpoint URL
     */
    public void setBaseUrl(String baseUrl) {
        if (env == null) {
            env = new HashMap<>();
        }
        if (baseUrl != null) {
            env.put("ANTHROPIC_BASE_URL", baseUrl);
        } else {
            env.remove("ANTHROPIC_BASE_URL");
        }
    }

    /**
     * Gets custom API base URL from environment variables.
     */
    @JsonIgnore
    public String getBaseUrl() {
        return env != null ? env.get("ANTHROPIC_BASE_URL") : null;
    }

    /**
     * Removes auth token from environment variables
     */
    public void removeAuthToken() {
        if (env != null) {
            env.remove("ANTHROPIC_AUTH_TOKEN");
        }
    }

    /**
     * Removes base URL from environment variables
     */
    public void removeBaseUrl() {
        if (env != null) {
            env.remove("ANTHROPIC_BASE_URL");
        }
    }

    /**
     * Gets auth token from environment variables
     * @JsonIgnore prevents Jackson from serializing this as a separate field
     */
    @JsonIgnore
    public String getAuthToken() {
        return env != null ? env.get("ANTHROPIC_AUTH_TOKEN") : null;
    }

    /**
     * Creates minimal default settings with only env block.
     * Does NOT include permissions - those are high-risk security settings
     * that should be preserved from original file or left to Claude Code defaults.
     */
    public static ClaudeSettings createDefault() {
        return ClaudeSettings.builder()
                .build();
    }
}
