package io.github.samzhu.gate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Configuration for OAuth2 connection to custom Claude API endpoint.
 * Stored in ~/.gate-cli/config.json
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionConfig {

    private String version;
    private Settings settings;
    private CurrentConnection currentConnection;
    private BackupSettings backupSettings;
    private String originalSettingsBackup;

    /**
     * User-configurable settings managed by 'config' command.
     * These override application.yaml defaults.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Settings {
        private String apiUrl;
        private String issuerUri;
        private String clientId;
        private String clientSecret;
    }

    /**
     * Current active connection information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentConnection {
        /**
         * Authentication type: "pkce" for login, "client_credentials" for connect
         */
        private String authType;
        private String clientId;
        private String clientSecret;
        private String issuerUri;
        private String tokenUrl;
        private String apiUrl;
        private Instant lastConnected;
        private Instant tokenExpiration;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BackupSettings {
        @Builder.Default
        private Integer maxBackups = 10;
        @Builder.Default
        private String backupDirectory = "~/.gate-cli/backups";
    }
}
