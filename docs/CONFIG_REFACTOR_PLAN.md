# Gate-CLI 配置管理重構計劃書

## 文件資訊
- **版本**: 3.0.0
- **日期**: 2025-11-28
- **功能**: 統一配置管理、簡化 login/connect 命令

---

## 1. 功能概述

### 1.1 目標

重構 gate-cli 的配置管理機制，讓所有 OAuth2 相關設定透過 `config` 命令統一管理，簡化 `login` 和 `connect` 命令的使用方式。

### 1.2 設計原則

1. **統一配置**：所有設定透過 `config` 命令寫入 `~/.gate-cli/config.json`
2. **無預設值**：`application.yaml` 不提供業務相關預設值
3. **簡化命令**：`login` 和 `connect` 無需參數，從配置讀取
4. **OIDC Discovery**：透過 `issuer-uri` 自動取得 OAuth2 端點

---

## 2. 命令設計

### 2.1 config 命令

**功能**：管理 gate-cli 設定，寫入 `~/.gate-cli/config.json`

**語法**：
```bash
# 設定單一或多個參數
gate-cli config --client-id "xxx"
gate-cli config --client-secret "yyy"
gate-cli config --issuer-uri "https://auth.xxx.cloud/"
gate-cli config --api-url "https://api.example.com/"

# 同時設定多個參數
gate-cli config \
  --client-id "xxx" \
  --client-secret "yyy" \
  --issuer-uri "https://auth.xxx.cloud/" \
  --api-url "https://api.example.com"

# 查看設定（無參數或 --list）
gate-cli config
gate-cli config --list

# 重置設定
gate-cli config --reset
```

**參數定義**：

| 參數 | 縮寫 | 必填 | 說明 | 使用者 |
|------|------|------|------|--------|
| `--client-id` | `-i` | 否 | OAuth2 Client ID | login, connect |
| `--client-secret` | `-s` | 否 | OAuth2 Client Secret | connect |
| `--issuer-uri` | `-u` | 否 | OIDC Issuer URI | login, connect |
| `--api-url` | `-a` | 否 | Claude API 端點 URL | login, connect |
| `--list` | `-l` | 否 | 顯示目前設定 | - |
| `--reset` | `-r` | 否 | 重置所有設定 | - |

**注意**：
- `config` 命令**不做備份**
- 無參數時等同 `--list`
- 固定值不需配置：`scope=openid`、`callback-port=8080`

### 2.2 login 命令

**功能**：OAuth2 Authorization Code Flow + PKCE（使用者互動登入）

**語法**：
```bash
gate-cli login
```

**必要配置**（需事先設定）：
- `client-id`
- `issuer-uri`
- `api-url`

**流程**：
```
1. 讀取配置 (client-id, issuer-uri, api-url)
2. 驗證必要配置存在
3. OIDC Discovery → 取得 authorization_endpoint, token_endpoint
4. 生成 PKCE (code_verifier, code_challenge)
5. 生成 state (防 CSRF)
6. 啟動本地回調伺服器 (localhost:8080/callback)
7. 開啟瀏覽器 → 授權頁面
8. 使用者登入授權
9. 接收回調 → 驗證 state → 取得 authorization_code
10. 交換 Token (POST token_endpoint)
11. 更新 Claude Code 設定 (~/.claude/settings.json)
12. 儲存連線狀態
```

### 2.3 connect 命令

**功能**：OAuth2 Client Credentials Flow（M2M 自動登入）

**語法**：
```bash
gate-cli connect
```

**必要配置**（需事先設定）：
- `client-id`
- `client-secret`
- `issuer-uri`
- `api-url`

**流程**：
```
1. 讀取配置 (client-id, client-secret, issuer-uri, api-url)
2. 驗證必要配置存在
3. OIDC Discovery → 取得 token_endpoint
4. OAuth2 Client Credentials 認證 (POST token_endpoint)
5. 更新 Claude Code 設定 (~/.claude/settings.json)
6. 儲存連線狀態
```

### 2.4 status 命令

**功能**：顯示目前設定與連線狀態

**輸出範例**：
```
Gate-CLI Status
===============

Configuration:
  Client ID:     dec4c670-6798-4257-8c56-2940109c7b20
  Client Secret: ****7b20
  Issuer URI:    https://auth.xxx.cloud/
  API URL:       https://api.example.com

Connection:
  Status: Connected
  Auth Type: OAuth2 PKCE (Public Client)
  Token Status: Valid (expires in 55 minutes)
  Last Connected: 2025-11-28 10:30:45

Claude Code Settings:
  Location: ~/.claude/settings.json
  Last Modified: 2025-11-28 10:30:46

Available Backups: 3
```

---

## 3. 配置檔案結構

### 3.1 ~/.gate-cli/config.json

```json
{
  "version": "3.0",
  "settings": {
    "clientId": "dec4c670-6798-4257-8c56-2940109c7b20",
    "clientSecret": "my-client-secret",
    "issuerUri": "https://auth.xxx.cloud/",
    "apiUrl": "https://api.example.com"
  },
  "currentConnection": {
    "authType": "pkce",
    "clientId": "dec4c670-6798-4257-8c56-2940109c7b20",
    "issuerUri": "https://auth.xxx.cloud/",
    "apiUrl": "https://api.example.com",
    "lastConnected": "2025-11-28T10:30:45Z",
    "tokenExpiration": "2025-11-28T11:30:45Z"
  },
  "backupSettings": {
    "maxBackups": 10,
    "backupDirectory": "~/.gate-cli/backups"
  },
  "originalSettingsBackup": "~/.gate-cli/backups/settings.json.original"
}
```

### 3.2 欄位說明

| 區段 | 欄位 | 說明 |
|------|------|------|
| `settings` | `clientId` | OAuth2 Client ID |
| `settings` | `clientSecret` | OAuth2 Client Secret（connect 專用） |
| `settings` | `issuerUri` | OIDC Issuer URI |
| `settings` | `apiUrl` | Claude API 端點 URL |
| `currentConnection` | `authType` | 認證類型：`pkce` 或 `client_credentials` |
| `currentConnection` | `tokenExpiration` | Token 過期時間 |

---

## 4. 技術實作

### 4.1 檔案變更清單

| 檔案 | 變更類型 | 說明 |
|------|----------|------|
| `model/ConnectionConfig.java` | 修改 | Settings 新增 `clientSecret` |
| `command/ConfigurationCommands.java` | 修改 | config 新增 `--client-secret`，移除 `--scope`、`--callback-port` |
| `command/ConnectionCommands.java` | 修改 | connect 移除所有參數 |
| `service/ConfigurationService.java` | 修改 | 新增 clientSecret 管理方法 |
| `config/GateCliProperties.java` | 修改 | 移除預設值，僅保留 scope、callback-port |
| `application.yaml` | 修改 | 移除 gate-cli.* 預設值 |

### 4.2 ConnectionConfig.Settings 修改

**檔案**：`src/main/java/io/github/samzhu/gate/model/ConnectionConfig.java`

**變更**：Settings 內部類別新增 `clientSecret` 欄位

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public static class Settings {
    private String apiUrl;
    private String issuerUri;
    private String clientId;
    private String clientSecret;  // 新增
}
```

### 4.3 ConfigurationService 修改

**檔案**：`src/main/java/io/github/samzhu/gate/service/ConfigurationService.java`

**新增方法**：

```java
/**
 * Sets the Client Secret in settings.
 */
public void setClientSecret(String clientSecret) {
    updateSettings(settings -> settings.setClientSecret(clientSecret));
    log.debug("Set Client Secret: ****");
}

/**
 * Gets the effective Client Secret (config.json only, no yaml fallback).
 */
public String getEffectiveClientSecret() {
    ConnectionConfig config = readConfig();
    if (config != null && config.getSettings() != null) {
        return config.getSettings().getClientSecret();
    }
    return null;
}
```

**移除方法**：
- `setScope()` - 改為固定值
- `setCallbackPort()` - 改為固定值
- `getEffectiveScope()` - 改為回傳固定值 `"openid"`
- `getEffectiveCallbackPort()` - 改為回傳固定值 `8080`

**修改方法**：

```java
/**
 * Gets the effective scope (fixed value).
 */
public String getEffectiveScope() {
    return "openid";
}

/**
 * Gets the effective callback port (fixed value).
 */
public int getEffectiveCallbackPort() {
    return 8080;
}
```

### 4.4 ConfigurationCommands.config() 修改

**檔案**：`src/main/java/io/github/samzhu/gate/command/ConfigurationCommands.java`

**變更**：

```java
@Command(command = "config", description = "Manage gate-cli configuration")
public String config(
        @Option(longNames = "client-id", shortNames = 'i',
                description = "Set OAuth2 client ID") String clientId,
        @Option(longNames = "client-secret", shortNames = 's',
                description = "Set OAuth2 client secret") String clientSecret,  // 新增
        @Option(longNames = "issuer-uri", shortNames = 'u',
                description = "Set OAuth2 issuer URI") String issuerUri,
        @Option(longNames = "api-url", shortNames = 'a',
                description = "Set Claude API endpoint URL") String apiUrl,
        @Option(longNames = "list", shortNames = 'l',
                description = "List current configuration", defaultValue = "false") boolean list,
        @Option(longNames = "reset", shortNames = 'r',
                description = "Reset to default values", defaultValue = "false") boolean reset
) {
    // ... 實作邏輯
}
```

**移除參數**：
- `--scope` / `-s`（注意：`-s` 改為 `--client-secret` 使用）
- `--callback-port` / `-p`

**showConfiguration() 修改**：

```java
private String showConfiguration() {
    StringBuilder output = new StringBuilder();
    output.append("Gate-CLI Configuration\n");
    output.append("======================\n\n");

    String clientId = configurationService.getEffectiveClientId();
    String clientSecret = configurationService.getEffectiveClientSecret();
    String issuerUri = configurationService.getEffectiveIssuerUri();
    String apiUrl = configurationService.getEffectiveApiUrl();

    output.append("Settings:\n");
    output.append("  Client ID:     ").append(valueOrNotSet(clientId)).append("\n");
    output.append("  Client Secret: ").append(maskSecret(clientSecret)).append("\n");
    output.append("  Issuer URI:    ").append(valueOrNotSet(issuerUri)).append("\n");
    output.append("  API URL:       ").append(valueOrNotSet(apiUrl)).append("\n");

    output.append("\nConfig file: ~/.gate-cli/config.json\n");

    return output.toString();
}

/**
 * Masks secret value for display (shows last 4 chars).
 */
private String maskSecret(String secret) {
    if (secret == null || secret.isEmpty()) {
        return "(not set)";
    }
    if (secret.length() <= 4) {
        return "****";
    }
    return "****" + secret.substring(secret.length() - 4);
}
```

### 4.5 ConnectionCommands.connect() 修改

**檔案**：`src/main/java/io/github/samzhu/gate/command/ConnectionCommands.java`

**變更**：移除所有參數，從配置讀取

```java
@Command(command = "connect", description = "Connect to OAuth2 server using client credentials (M2M)")
public String connect() {
    try {
        StringBuilder output = new StringBuilder();

        // 1. Read configuration
        String clientId = configurationService.getEffectiveClientId();
        String clientSecret = configurationService.getEffectiveClientSecret();
        String issuerUri = configurationService.getEffectiveIssuerUri();
        String apiUrl = configurationService.getEffectiveApiUrl();

        // 2. Validate required settings
        if (isEmpty(clientId)) {
            return "✗ Missing configuration: client-id\n" +
                   "Use 'config --client-id <id>' to set it.\n";
        }
        if (isEmpty(clientSecret)) {
            return "✗ Missing configuration: client-secret\n" +
                   "Use 'config --client-secret <secret>' to set it.\n";
        }
        if (isEmpty(issuerUri)) {
            return "✗ Missing configuration: issuer-uri\n" +
                   "Use 'config --issuer-uri <url>' to set it.\n";
        }
        if (isEmpty(apiUrl)) {
            return "✗ Missing configuration: api-url\n" +
                   "Use 'config --api-url <url>' to set it.\n";
        }

        // 3. OIDC Discovery
        output.append("→ Discovering token endpoint...\n");
        OIDCConfiguration oidcConfig = oidcDiscoveryService.discover(issuerUri);
        String tokenUrl = oidcConfig.getTokenEndpoint();

        // 4. Check if already connected
        if (claudeConfigService.settingsExist() && configurationService.isConnected()) {
            output.append("⚠ Claude Code settings already configured.\n");
            output.append("Use 'disconnect' first or continue to overwrite.\n");
        }

        // 5. Backup original settings if first connection
        if (!configurationService.isConnected()) {
            claudeConfigService.ensureOriginalBackup();
        }

        // 6. OAuth2 Client Credentials authentication
        output.append("→ Connecting to OAuth2 server...\n");
        OAuth2TokenResponse tokenResponse = oauth2Service.getAccessToken(
                clientId, clientSecret, tokenUrl);

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            return "✗ Failed to obtain access token from OAuth2 server";
        }

        output.append("✓ Connected to OAuth2 server\n");
        output.append("✓ Obtained access token\n");

        // 7. Update Claude Code settings
        output.append("→ Updating Claude Code settings...\n");
        claudeConfigService.updateSettings(apiUrl, tokenResponse.getTokenForAuth(), true);
        output.append("✓ Updated Claude Code settings\n");

        // 8. Save connection configuration
        configurationService.saveConnection(
                clientId, clientSecret, tokenUrl, apiUrl,
                tokenResponse.getExpiresAt());
        output.append("✓ Saved connection configuration\n");

        // 9. Display summary
        output.append("\n");
        output.append("Configuration Summary:\n");
        output.append("  API URL: ").append(apiUrl).append("\n");
        output.append("  Auth Type: OAuth2 Client Credentials (M2M)\n");
        if (tokenResponse.getExpiresAt() != null) {
            output.append("  Token expires: ")
                    .append(TIMESTAMP_FORMAT.format(tokenResponse.getExpiresAt()))
                    .append("\n");
        }
        output.append("  Settings file: ").append(claudeConfigService.getSettingsPath()).append("\n");

        return output.toString();

    } catch (Exception e) {
        return formatError("Connection failed", e);
    }
}
```

### 4.6 GateCliProperties 修改

**檔案**：`src/main/java/io/github/samzhu/gate/config/GateCliProperties.java`

**變更**：移除業務預設值，僅保留技術性預設值

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "gate-cli")
public class GateCliProperties {

    /**
     * Claude API endpoint URL.
     * No default - must be configured via 'config' command.
     */
    private String apiUrl = "";

    /**
     * OAuth2 Issuer URI for OIDC Discovery.
     * No default - must be configured via 'config' command.
     */
    private String issuerUri = "";

    /**
     * OAuth2 Client ID.
     * No default - must be configured via 'config' command.
     */
    private String clientId = "";

    /**
     * OAuth2 scope for authorization (fixed value).
     */
    private String scope = "openid";

    /**
     * Local port for OAuth callback server (fixed value).
     */
    private int callbackPort = 8080;
}
```

### 4.7 application.yaml 修改

**檔案**：`src/main/resources/application.yaml`

**變更**：移除 gate-cli.* 區段或清空值

```yaml
spring:
  application:
    name: gate-cli

  main:
    web-application-type: none
    banner-mode: console

  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration

spring.shell:
  interactive:
    enabled: true
  history:
    enabled: true
    name: gate-cli
  command:
    help:
      enabled: true

logging:
  level:
    root: INFO
    io.github.samzhu.gate: INFO
  pattern:
    console: "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"

management:
  endpoints:
    enabled-by-default: false
  endpoint:
    health:
      enabled: true

# Gate-CLI configuration
# All settings should be configured via 'config' command
# These are internal defaults only
gate-cli:
  scope: openid
  callback-port: 8080
```

### 4.8 ConfigurationCommands.status() 修改

**檔案**：`src/main/java/io/github/samzhu/gate/command/ConfigurationCommands.java`

**變更**：增強顯示配置資訊

```java
@Command(command = "status", description = "Show current connection status and configuration")
public String status() {
    StringBuilder output = new StringBuilder();

    output.append("Gate-CLI Status\n");
    output.append("===============\n\n");

    // Configuration section
    output.append("Configuration:\n");
    String clientId = configurationService.getEffectiveClientId();
    String clientSecret = configurationService.getEffectiveClientSecret();
    String issuerUri = configurationService.getEffectiveIssuerUri();
    String apiUrl = configurationService.getEffectiveApiUrl();

    output.append("  Client ID:     ").append(valueOrNotSet(clientId)).append("\n");
    output.append("  Client Secret: ").append(maskSecret(clientSecret)).append("\n");
    output.append("  Issuer URI:    ").append(valueOrNotSet(issuerUri)).append("\n");
    output.append("  API URL:       ").append(valueOrNotSet(apiUrl)).append("\n");
    output.append("\n");

    // Connection status section
    output.append("Connection:\n");
    if (configurationService.isConnected()) {
        ConnectionConfig.CurrentConnection connection = configurationService.getCurrentConnection();

        output.append("  Status: Connected\n");

        // Auth type
        String authType = connection.getAuthType();
        if ("pkce".equals(authType)) {
            output.append("  Auth Type: OAuth2 PKCE (Public Client)\n");
        } else {
            output.append("  Auth Type: OAuth2 Client Credentials (M2M)\n");
        }

        // Token status
        if (connection.getTokenExpiration() != null) {
            Duration timeUntil = oauth2Service.getTimeUntilExpiration(connection.getTokenExpiration());
            if (timeUntil != null) {
                output.append("  Token Status: Valid (expires in ")
                        .append(oauth2Service.formatDuration(timeUntil))
                        .append(")\n");
            } else {
                output.append("  Token Status: Expired\n");
                output.append("    → Use 'refresh' or 'login'/'connect' to obtain a new token\n");
            }
        }

        // Last connected
        if (connection.getLastConnected() != null) {
            output.append("  Last Connected: ")
                    .append(TIMESTAMP_FORMAT.format(connection.getLastConnected()))
                    .append("\n");
        }
    } else {
        output.append("  Status: Not connected\n");
        output.append("\n");
        output.append("  Use 'login' for interactive browser login (PKCE)\n");
        output.append("  Use 'connect' for M2M authentication (Client Credentials)\n");
    }

    // Backup information
    output.append("\n");
    List<BackupService.BackupInfo> backups = backupService.listBackups();
    output.append("Available Backups: ").append(backups.size()).append("\n");

    // Claude Code settings
    output.append("\nClaude Code Settings:\n");
    output.append("  Location: ").append(claudeConfigService.getSettingsPath()).append("\n");

    if (claudeConfigService.settingsExist()) {
        String lastModified = claudeConfigService.getLastModifiedTime();
        if (lastModified != null) {
            Instant instant = Instant.parse(lastModified);
            output.append("  Last Modified: ")
                    .append(TIMESTAMP_FORMAT.format(instant))
                    .append("\n");
        }
    } else {
        output.append("  Status: File does not exist\n");
    }

    return output.toString();
}
```

---

## 5. 實作順序

### Phase 1: 模型層修改
1. 修改 `ConnectionConfig.Settings` 新增 `clientSecret` 欄位
2. 確認 GraalVM 反射註冊（如需要）

### Phase 2: 服務層修改
1. 修改 `ConfigurationService`
   - 新增 `setClientSecret()` 方法
   - 新增 `getEffectiveClientSecret()` 方法
   - 修改 `getEffectiveScope()` 回傳固定值
   - 修改 `getEffectiveCallbackPort()` 回傳固定值

### Phase 3: 命令層修改
1. 修改 `ConfigurationCommands.config()`
   - 新增 `--client-secret` 參數
   - 移除 `--scope`、`--callback-port` 參數
   - 更新 `showConfiguration()` 顯示 client-secret（遮罩）
   - 新增 `maskSecret()` 輔助方法

2. 修改 `ConfigurationCommands.status()`
   - 新增配置區段顯示

3. 修改 `ConnectionCommands.connect()`
   - 移除所有參數
   - 從配置讀取所有設定

### Phase 4: 配置檔修改
1. 修改 `GateCliProperties` 移除業務預設值
2. 修改 `application.yaml` 移除 gate-cli.* 預設值

### Phase 5: 測試驗證
1. 編譯專案：`./gradlew clean build`
2. 測試 config 命令
3. 測試 login 命令
4. 測試 connect 命令
5. 測試 status 命令

---

## 6. 測試案例

### 6.1 config 命令測試

| 測試案例 | 預期結果 |
|----------|----------|
| `config --client-id "test"` | 設定成功，顯示確認訊息 |
| `config --client-secret "secret"` | 設定成功，顯示確認訊息 |
| `config` | 顯示所有設定，client-secret 遮罩顯示 |
| `config --reset` | 清除所有設定 |

### 6.2 connect 命令測試

| 測試案例 | 預期結果 |
|----------|----------|
| 未設定配置執行 `connect` | 顯示缺少配置的錯誤訊息 |
| 設定完整配置執行 `connect` | 成功連線 |
| 設定不完整執行 `connect` | 顯示缺少哪個配置 |

### 6.3 login 命令測試

| 測試案例 | 預期結果 |
|----------|----------|
| 未設定配置執行 `login` | 顯示缺少配置的錯誤訊息 |
| 設定 client-id, issuer-uri, api-url 執行 `login` | 開啟瀏覽器授權 |

### 6.4 status 命令測試

| 測試案例 | 預期結果 |
|----------|----------|
| 未連線狀態 | 顯示配置和「未連線」狀態 |
| 已連線狀態 | 顯示配置、連線資訊、Token 狀態 |

---

## 7. 使用範例

### 7.1 首次設定與 PKCE 登入

```bash
# 1. 設定配置
$ gate-cli config \
    --client-id "dec4c670-6798-4257-8c56-2940109c7b20" \
    --issuer-uri "https://auth.xxx.cloud/" \
    --api-url "https://api.example.com"

✓ Client ID set to: dec4c670-6798-4257-8c56-2940109c7b20
✓ Issuer URI set to: https://auth.xxx.cloud/
✓ API URL set to: https://api.example.com

# 2. 確認配置
$ gate-cli config

Gate-CLI Configuration
======================

Settings:
  Client ID:     dec4c670-6798-4257-8c56-2940109c7b20
  Client Secret: (not set)
  Issuer URI:    https://auth.xxx.cloud/
  API URL:       https://api.example.com

Config file: ~/.gate-cli/config.json

# 3. 登入
$ gate-cli login

→ Starting OAuth2 login...
  Issuer:    https://auth.xxx.cloud/
  Client ID: dec4c670-6798-4257-8c56-2940109c7b20
→ Discovering OIDC configuration...
→ Opening browser for authorization...
→ Waiting for authorization (timeout: 5 minutes)...
✓ Login successful
✓ Obtained access token
✓ Updated Claude Code settings
✓ Saved connection configuration

Configuration Summary:
  API URL: https://api.example.com
  Auth Type: OAuth2 PKCE (Public Client)
  Token expires: 2025-11-28 15:30:45
  Settings file: ~/.claude/settings.json
```

### 7.2 M2M 連線 (Client Credentials)

```bash
# 1. 設定配置（包含 client-secret）
$ gate-cli config \
    --client-id "service-account" \
    --client-secret "my-secret-key" \
    --issuer-uri "https://auth.xxx.cloud/" \
    --api-url "https://api.example.com"

✓ Client ID set to: service-account
✓ Client Secret set
✓ Issuer URI set to: https://auth.xxx.cloud/
✓ API URL set to: https://api.example.com

# 2. 連線
$ gate-cli connect

→ Discovering token endpoint...
→ Connecting to OAuth2 server...
✓ Connected to OAuth2 server
✓ Obtained access token
→ Updating Claude Code settings...
✓ Updated Claude Code settings
✓ Saved connection configuration

Configuration Summary:
  API URL: https://api.example.com
  Auth Type: OAuth2 Client Credentials (M2M)
  Token expires: 2025-11-28 15:30:45
  Settings file: ~/.claude/settings.json
```

---

## 8. 注意事項

### 8.1 安全性
- `client-secret` 以明文儲存於 `~/.gate-cli/config.json`
- 建議設定檔案權限為 `600`（僅擁有者可讀寫）
- `status` 和 `config` 命令顯示時會遮罩 secret

### 8.2 向後相容性
- 此版本不向後相容舊版配置格式
- 使用者需重新執行 `config` 命令設定

### 8.3 OIDC Discovery 失敗處理
- 若 issuer-uri 無法存取，應顯示清楚的錯誤訊息
- 建議使用者檢查網路連線和 URL 正確性

---

## 文件核准

| 角色 | 確認 |
|------|------|
| 需求確認 | [ ] |
| 技術審查 | [ ] |
| 開始實作 | [ ] |
