package io.github.samzhu.gate.model;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClaudeSettings {

    /**
     * API key (may be "not-used" when using custom endpoint)
     */
    private String apiKey;

    /**
     * Custom endpoint URL for Claude API
     */
    private String customEndpoint;

    /**
     * Environment variables (including ANTHROPIC_AUTH_TOKEN)
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
     * Sets bearer token in environment variables
     */
    public void setBearerToken(String bearerToken) {
        if (env == null) {
            env = new HashMap<>();
        }
        env.put("ANTHROPIC_AUTH_TOKEN", bearerToken);
    }

    /**
     * Removes bearer token from environment variables
     */
    public void removeBearerToken() {
        if (env != null) {
            env.remove("ANTHROPIC_AUTH_TOKEN");
        }
    }

    /**
     * Gets bearer token from environment variables
     */
    public String getBearerToken() {
        return env != null ? env.get("ANTHROPIC_AUTH_TOKEN") : null;
    }

    /**
     * Creates a minimal default settings
     */
    public static ClaudeSettings createDefault() {
        return ClaudeSettings.builder()
                .apiKey("not-used")
                .permissions(Permissions.builder()
                        .allow(List.of("*"))
                        .deny(List.of())
                        .ask(List.of())
                        .build())
                .build();
    }
}
