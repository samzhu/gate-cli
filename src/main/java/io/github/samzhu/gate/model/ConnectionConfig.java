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
    private CurrentConnection currentConnection;
    private BackupSettings backupSettings;
    private String originalSettingsBackup;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentConnection {
        private String clientId;
        private String clientSecret;
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
