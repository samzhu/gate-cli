# Gate-CLI: 產品需求文件

## 文件資訊
- **版本**: 1.0.0
- **最後更新**: 2025-11-24
- **專案**: gate-cli
- **技術堆疊**: Spring Boot 3.5.8, Spring Shell 3.4.1, Java 25, GraalVM Native
- **目標平台**: 跨平台 CLI (macOS, Linux, Windows)
- **發布方式**: GraalVM 原生可執行檔 (無需 Java 環境)

---

## 1. 執行摘要

**Gate-CLI** 是一個命令列介面工具,用於自動化配置 Claude Code 以使用受 OAuth2 保護的自訂 API 端點。它實作了 OAuth2 Client Credentials 流程來取得存取權杖,並自動配置 Claude Code 的設定檔案,包含適當的端點 URL 和 bearer token。

### 核心價值主張
- **自動化認證**: 消除 Claude Code 的手動 token 管理工作
- **配置安全性**: 備份和還原機制防止配置遺失
- **開發者體驗**: 用簡單的 CLI 指令處理複雜的 OAuth 流程
- **企業就緒**: 支援企業部署的自訂 OAuth2 端點
- **原生執行**: GraalVM 編譯為原生可執行檔,快速啟動 (< 100ms),無需 Java 環境

---

## 2. 背景與動機

### 問題陳述
組織在 OAuth2 認證後方部署自訂 Claude API 閘道時面臨幾個挑戰:
1. 手動取得 token 和配置容易出錯
2. Token 過期需要重複手動更新
3. 手動編輯 Claude Code 設定時有損壞的風險
4. 沒有簡單的方法在不同配置之間切換或還原原始設定

### 解決方案概述
Gate-CLI 自動化整個工作流程:
1. 使用 client credentials 向 OAuth2 伺服器認證
2. 取得並驗證 bearer tokens
3. 使用原子寫入安全地更新 Claude Code 設定
4. 維護配置備份以便輕鬆還原

---

## 3. 參考文件與架構設計

### 核心架構設計原則

**採用的設計模式:**
- **單一事實來源 (SOT)**: 維護獨立於實際設定的主配置檔案
- **原子寫入**: 使用臨時檔案 + 重新命名模式防止損壞
- **自動輪替備份**: 保留最近 10 個備份並自動輪替
- **雙向同步**: 切換時寫入實際檔案,需要時從實際檔案讀取

**架構原則:**
- 分層設計: Commands → Services → Models
- 配置作為單一事實來源
- 所有操作的回滾保護

### Claude Code 設定文件
**網址**: https://code.claude.com/docs/en/settings

**關鍵配置細節:**
- 設定檔位置: `~/.claude/settings.json`
- Bearer token 配置: `ANTHROPIC_AUTH_TOKEN` 環境變數
- 替代方案: `apiKeyHelper` 用於動態憑證生成
- JSON 結構包含 permissions、env 等區段

### Spring Shell 3.4.1 文件

**命令註冊 (基於註解):**
- 網址: https://docs.spring.io/spring-shell/reference/commands/registration/annotation.html
- 使用 `@Command` 註解標記命令方法
- 在 Spring Boot 應用類別上使用 `@CommandScan` 啟用
- 類別層級的 `@Command` 用於分組相關命令

**命令註冊 (程式化):**
- 網址: https://docs.spring.io/spring-shell/reference/commands/registration/programmatic.html
- 使用 `CommandRegistration.builder()` 建立動態命令
- 對複雜命令結構提供更大彈性

**命令選項 (基於註解):**
- 網址: https://docs.spring.io/spring-shell/reference/options/basics/annotation.html
- 使用 `@Option` 註解搭配 `longNames` 和 `shortNames`
- 支援必填/選填參數

**命令選項 (程式化):**
- 網址: https://docs.spring.io/spring-shell/reference/options/basics/programmatic.html
- Builder 模式用於選項定義
- 型別安全的選項處理

**命令可用性:**
- 網址: https://docs.spring.io/spring-shell/reference/commands/availability.html
- 使用 `@CommandAvailability` 搭配 `AvailabilityProvider`
- 基於狀態的命令啟用/停用
- 使用者友善的不可用訊息

**說明選項:**
- 網址: https://docs.spring.io/spring-shell/reference/commands/helpoptions.html
- 自動的 `--help` 和 `-h` 選項
- 可透過應用程式屬性配置
- 支援個別命令自訂

---

## 4. 功能需求

### 4.1 核心命令

#### 4.1.1 `connect` 命令
**目的**: 連線到 OAuth2 伺服器並配置 Claude Code

**語法**:
```bash
gate-cli connect --client-id <id> --client-secret <secret> --token-url <url> --api-url <url>
```

**選項**:
| 選項 | 縮寫 | 必填 | 說明 |
|------|------|------|------|
| --client-id | -i | 是 | OAuth2 客戶端 ID |
| --client-secret | -s | 是 | OAuth2 客戶端密鑰 |
| --token-url | -t | 是 | OAuth2 token 端點 URL |
| --api-url | -a | 是 | 自訂 Claude API 端點 URL |
| --force | -f | 否 | 不提示直接覆寫 |

**行為**:
1. 驗證所有輸入參數
2. 檢查 Claude Code 設定檔是否存在
3. 如果設定檔存在且未使用 --force:
   - 在 `~/.gate-cli/backups/` 建立自動備份
   - 時間戳記備份: `settings.json.backup.YYYY-MM-DD-HH-mm-ss`
4. 執行 OAuth2 client credentials 流程:
   - POST 到 token URL 並附上客戶端憑證
   - 從回應解析存取權杖
   - 驗證 token 結構
5. 讀取現有的 Claude Code 設定 (如果存在)
6. 合併/更新設定:
   - 自訂 API 端點 URL
   - 在適當的配置欄位中設定 Bearer token
7. 原子性寫入設定:
   - 寫入臨時檔案: `~/.claude/settings.json.tmp`
   - 驗證 JSON 結構
   - 將臨時檔案重新命名為 `~/.claude/settings.json`
8. 將連線配置儲存在 `~/.gate-cli/config.json`
9. 顯示成功訊息及配置摘要

**輸出範例**:
```
✓ 已連線到 OAuth2 伺服器
✓ 已取得存取權杖
✓ 已建立備份: ~/.gate-cli/backups/settings.json.backup.2025-11-24-10-30-45
✓ 已更新 Claude Code 設定

配置摘要:
  API URL: https://api.example.com/v1
  Token 過期時間: 2025-11-24 11:30:45
  設定檔: ~/.claude/settings.json
```

**錯誤處理**:
- OAuth2 認證失敗: 顯示錯誤訊息並退出
- 無效的 token 回應: 解析錯誤詳情並建議修正
- 檔案寫入權限問題: 建議 chmod/chown 指令
- 網路連線問題: 建議檢查 token URL 的可存取性

#### 4.1.2 `restore` 命令
**目的**: 還原先前的 Claude Code 設定

**語法**:
```bash
gate-cli restore [--backup <file>]
```

**選項**:
| 選項 | 縮寫 | 必填 | 說明 |
|------|------|------|------|
| --backup | -b | 否 | 指定要還原的備份檔案 |
| --list | -l | 否 | 列出可用的備份 |

**行為**:
1. 如果提供 `--list` 旗標:
   - 顯示 `~/.gate-cli/backups/` 中所有備份及時間戳記
   - 顯示檔案大小和建立日期
   - 顯示後退出
2. 如果指定 `--backup`:
   - 驗證備份檔案存在
   - 使用指定的備份
3. 如果未指定 `--backup`:
   - 使用自動輪替清單中最新的備份
4. 驗證備份檔案是有效的 JSON
5. 在還原前先備份目前設定
6. 原子性寫入備份內容到 `~/.claude/settings.json`
7. 顯示成功訊息

**輸出範例**:
```
✓ 目前設定已備份至: ~/.gate-cli/backups/settings.json.backup.2025-11-24-11-00-00
✓ 設定已從以下位置還原: ~/.gate-cli/backups/settings.json.backup.2025-11-24-10-00-00

還原的配置:
  設定檔: ~/.claude/settings.json
```

#### 4.1.3 `status` 命令
**目的**: 顯示目前的連線狀態和配置

**語法**:
```bash
gate-cli status
```

**行為**:
1. 讀取 `~/.gate-cli/config.json`
2. 讀取 `~/.claude/settings.json`
3. 顯示:
   - 連線狀態 (已連線/未連線)
   - 目前的 API 端點 URL
   - Token 過期時間 (如果可用)
   - 最後連線時間
   - 可用備份數量
4. 如果已連線,驗證 token 尚未過期

**輸出範例**:
```
Gate-CLI 狀態
===============
狀態: 已連線
API URL: https://api.example.com/v1
Token 狀態: 有效 (55 分鐘後過期)
最後連線: 2025-11-24 10:30:45
可用備份: 3

Claude Code 設定:
  位置: ~/.claude/settings.json
  最後修改: 2025-11-24 10:30:46
```

#### 4.1.4 `disconnect` 命令
**目的**: 移除自訂配置並還原原始設定

**語法**:
```bash
gate-cli disconnect
```

**行為**:
1. 檢查自訂配置是否存在
2. 建立目前設定的備份
3. 從設定中移除自訂 API 端點和 bearer token
4. 如果原始設定備份存在,則還原它
5. 否則,建立最小的預設設定
6. 更新 `~/.gate-cli/config.json` 狀態
7. 顯示成功訊息

**輸出範例**:
```
✓ 已建立備份: ~/.gate-cli/backups/settings.json.backup.2025-11-24-11-30-00
✓ 已移除自訂配置
✓ 已還原原始設定

狀態: 未連線
```

#### 4.1.5 `refresh` 命令
**目的**: 使用儲存的憑證重新整理存取權杖

**語法**:
```bash
gate-cli refresh
```

**可用性**: 僅在已連線時可用

**行為**:
1. 從 `~/.gate-cli/config.json` 讀取儲存的客戶端憑證
2. 執行 OAuth2 client credentials 流程
3. 使用新 token 更新設定
4. 顯示成功訊息及新的過期時間

**輸出範例**:
```
✓ Token 重新整理成功
新 token 過期時間: 2025-11-24 12:30:45
```

### 4.2 配置管理

#### 4.2.1 配置檔案結構
**位置**: `~/.gate-cli/config.json`

**結構**:
```json
{
  "version": "1.0",
  "currentConnection": {
    "clientId": "client-id",
    "clientSecret": "client-secret-plaintext",
    "tokenUrl": "https://oauth.example.com/token",
    "apiUrl": "https://api.example.com/v1",
    "lastConnected": "2025-11-24T10:30:45Z",
    "tokenExpiration": "2025-11-24T11:30:45Z"
  },
  "backupSettings": {
    "maxBackups": 10,
    "backupDirectory": "~/.gate-cli/backups"
  },
  "originalSettingsBackup": "~/.gate-cli/backups/settings.json.original"
}
```

**安全考量**:
- 明文儲存 (本機工具,簡化設計)
- 建議檔案權限: `600` (僅擁有者可讀寫)
- 依賴作業系統層級的檔案權限保護
- 可選: 如果權限過於寬鬆則顯示警告

#### 4.2.2 備份輪替策略
**目錄**: `~/.gate-cli/backups/`

**命名慣例**: `settings.json.backup.YYYY-MM-DD-HH-mm-ss`

**輪替邏輯**:
1. 保留最多 10 個備份 (可配置)
2. 建立第 11 個備份時:
   - 按時間戳記排序備份 (最舊的優先)
   - 刪除最舊的備份
   - 建立新備份
3. 特殊備份: `settings.json.original` (永不輪替)
   - 在第一次 `connect` 命令時建立
   - 由 `disconnect` 命令用於完整還原

---

## 5. 非功能性需求

### 5.1 效能
- 命令執行: < 2 秒 (不包括 OAuth2 網路呼叫)
- OAuth2 token 取得: < 5 秒 (依網路而定)
- 檔案操作: 原子性且防崩潰
- 啟動時間: < 1 秒用於命令解析

### 5.2 安全性
- 配置檔案明文儲存 (本機使用,依賴作業系統權限保護)
- 建議配置檔案權限設為 600 (僅擁有者可讀寫)
- 命令歷史記錄中避免直接輸入密鑰 (建議使用環境變數或配置檔)

### 5.3 可靠性
- 原子檔案寫入 (臨時 + 重新命名模式)
- 任何修改前自動備份
- 類事務行為: 失敗時回滾
- 每個步驟驗證並提供清晰的錯誤訊息
- 盡可能使命令冪等

### 5.4 可用性
- 清晰、可操作的錯誤訊息
- 長時間操作的進度指示器
- 彩色輸出 (成功=綠色,錯誤=紅色,資訊=藍色)
- 所有命令的內建說明 (`--help`)
- 破壞性操作的互動式提示
- 除錯用的詳細模式 (`--verbose`)

### 5.5 相容性
- 跨平台: macOS、Linux、Windows
- Claude Code 版本: 最新穩定版
- 編譯方式: GraalVM Native Image (不需要 Java 執行環境)
- 開發環境:
  - Java: 25
  - Spring Boot: 3.5.8
  - Spring Shell: 3.4.1
  - GraalVM: 最新穩定版

---

## 6. 技術架構

### 6.1 專案結構
```
gate-cli/
├── src/main/java/com/example/gatecli/
│   ├── GateCliApplication.java          # Spring Boot 應用程式,帶有 @CommandScan
│   ├── command/
│   │   ├── ConnectionCommands.java      # connect、disconnect、refresh
│   │   ├── ConfigurationCommands.java   # restore、status
│   │   └── availability/
│   │       └── ConnectedAvailability.java  # 可用性提供者
│   ├── service/
│   │   ├── OAuth2Service.java           # OAuth2 client credentials 流程
│   │   ├── ClaudeConfigService.java     # Claude Code 設定管理
│   │   ├── BackupService.java           # 備份建立和輪替
│   │   └── ConfigurationService.java    # 本地配置管理
│   ├── model/
│   │   ├── ConnectionConfig.java        # 連線配置 DTO
│   │   ├── OAuth2TokenResponse.java     # OAuth2 token 回應 DTO
│   │   └── ClaudeSettings.java          # Claude Code 設定 DTO
│   ├── exception/
│   │   ├── OAuth2Exception.java
│   │   ├── ConfigurationException.java
│   │   └── BackupException.java
│   └── util/
│       ├── FileUtil.java                # 原子檔案操作
│       └── ValidationUtil.java          # 輸入驗證
├── src/main/resources/
│   ├── application.yml                   # Spring Boot 配置
│   └── banner.txt                        # CLI 橫幅
├── src/test/java/                        # 單元和整合測試
├── build.gradle                          # Gradle 建構配置
└── README.md
```

### 6.2 核心元件

#### 6.2.1 命令層
- **職責**: 使用者互動、輸入驗證、命令編排
- **技術**: Spring Shell `@Command` 註解
- **核心類別**:
  - `ConnectionCommands`: 主要連線生命週期命令
  - `ConfigurationCommands`: 配置管理命令

**實作範例**:
```java
@Command(group = "Connection Management")
public class ConnectionCommands {

    @Autowired
    private OAuth2Service oauth2Service;

    @Autowired
    private ClaudeConfigService claudeConfigService;

    @Command(command = "connect", description = "連線到 OAuth2 伺服器並配置 Claude Code")
    public String connect(
        @Option(longNames = "client-id", shortNames = 'i', required = true,
                description = "OAuth2 客戶端 ID") String clientId,
        @Option(longNames = "client-secret", shortNames = 's', required = true,
                description = "OAuth2 客戶端密鑰") String clientSecret,
        @Option(longNames = "token-url", shortNames = 't', required = true,
                description = "OAuth2 token 端點 URL") String tokenUrl,
        @Option(longNames = "api-url", shortNames = 'a', required = true,
                description = "自訂 Claude API 端點 URL") String apiUrl,
        @Option(longNames = "force", shortNames = 'f',
                description = "不提示直接覆寫") boolean force
    ) {
        // 實作
    }
}
```

#### 6.2.2 服務層
- **職責**: 業務邏輯、外部整合、事務管理
- **核心服務**:
  - `OAuth2Service`: 處理 OAuth2 client credentials 流程
  - `ClaudeConfigService`: 管理 Claude Code 設定檔
  - `BackupService`: 建立和管理帶輪替的備份
  - `ConfigurationService`: 管理 gate-cli 配置

**OAuth2Service 範例**:
```java
@Service
public class OAuth2Service {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public OAuth2Service(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    public OAuth2TokenResponse getAccessToken(String registrationId) {
        // 使用 Spring Security OAuth2 Client
        // 自動處理 client credentials 流程
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
            .withClientRegistrationId(registrationId)
            .principal("gate-cli")
            .build();

        OAuth2AuthorizedClient authorizedClient =
            authorizedClientManager.authorize(authorizeRequest);

        // 取得 access token
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

        // 解析並返回 token 回應
        return new OAuth2TokenResponse(
            accessToken.getTokenValue(),
            accessToken.getExpiresAt()
        );
    }

    public boolean validateToken(OAuth2AccessToken token) {
        // 驗證 token 是否過期
        return token.getExpiresAt() != null
            && token.getExpiresAt().isAfter(Instant.now());
    }
}
```

**ClaudeConfigService 範例**:
```java
@Service
public class ClaudeConfigService {

    @Autowired
    private FileUtil fileUtil;

    private static final String SETTINGS_PATH = System.getProperty("user.home") + "/.claude/settings.json";

    public ClaudeSettings readSettings() {
        // 讀取並解析 settings.json
    }

    public void updateSettings(String apiUrl, String bearerToken) {
        // 使用臨時檔案 + 重新命名進行原子更新
    }

    public void removeCustomConfig() {
        // 移除自訂 API 端點和 bearer token
    }
}
```

#### 6.2.3 模型層
- **職責**: 資料結構、DTOs、驗證
- **核心模型**:
  - `ConnectionConfig`: 儲存連線配置
  - `OAuth2TokenResponse`: OAuth2 回應解析
  - `ClaudeSettings`: Claude Code 設定結構

### 6.3 依賴管理

**實際專案依賴** (參考 build.gradle):
```gradle
ext {
    set('springShellVersion', "3.4.1")
}

dependencies {
    // Spring Boot 核心功能
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // OAuth2 客戶端支援
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

    // Spring Shell CLI 框架
    implementation 'org.springframework.shell:spring-shell-starter'

    // 可觀測性 (Tracing)
    implementation 'io.micrometer:micrometer-tracing-bridge-brave'
    runtimeOnly 'io.micrometer:micrometer-registry-otlp'

    // Lombok 簡化程式碼
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // 開發工具
    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    // 測試依賴
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.springframework.shell:spring-shell-starter-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.shell:spring-shell-dependencies:${springShellVersion}"
    }
}
```

**關鍵依賴說明**:
- **spring-boot-starter-oauth2-client**: 提供 OAuth2 客戶端支援
- **spring-shell-starter**: CLI 框架核心
- **micrometer-tracing**: 分散式追蹤支援
- **lombok**: 減少樣板程式碼
- **testcontainers**: 整合測試容器化支援

**版本資訊**:
- Spring Boot: 3.5.8
- Spring Shell: 3.4.1
- Java: 25
- GraalVM Native: 0.10.6

---

## 7. 使用者工作流程

### 7.1 首次設定
```bash
# 步驟 1: 連線到 OAuth2 伺服器
$ gate-cli connect \
  --client-id "my-client-id" \
  --client-secret "my-secret" \
  --token-url "https://oauth.example.com/token" \
  --api-url "https://api.example.com/v1"

✓ 已連線到 OAuth2 伺服器
✓ 已取得存取權杖
✓ 已建立備份: ~/.gate-cli/backups/settings.json.original
✓ 已更新 Claude Code 設定

# 步驟 2: 驗證配置
$ gate-cli status

Gate-CLI 狀態
===============
狀態: 已連線
API URL: https://api.example.com/v1
Token 狀態: 有效 (60 分鐘後過期)
```

### 7.2 Token 重新整理
```bash
# 手動重新整理
$ gate-cli refresh

✓ Token 重新整理成功
新 token 過期時間: 2025-11-24 12:30:45
```

### 7.3 配置還原
```bash
# 列出可用備份
$ gate-cli restore --list

可用備份:
1. settings.json.backup.2025-11-24-10-30-45 (2.1 KB)
2. settings.json.backup.2025-11-24-09-15-30 (2.0 KB)
3. settings.json.original (1.8 KB)

# 還原特定備份
$ gate-cli restore --backup settings.json.backup.2025-11-24-09-15-30

✓ 設定已從以下位置還原: ~/.gate-cli/backups/settings.json.backup.2025-11-24-09-15-30
```

### 7.4 斷線
```bash
# 移除自訂配置
$ gate-cli disconnect

✓ 已建立備份: ~/.gate-cli/backups/settings.json.backup.2025-11-24-11-30-00
✓ 已移除自訂配置
✓ 已還原原始設定

狀態: 未連線
```

---

## 8. 錯誤處理情境

### 8.1 OAuth2 認證失敗
**情境**: 無效的客戶端憑證

**錯誤訊息**:
```
✗ OAuth2 認證失敗
錯誤: invalid_client
說明: 客戶端認證失敗

疑難排解:
  • 驗證客戶端 ID 和密鑰是否正確
  • 檢查客戶端是否已在 token URL 註冊
  • 確保已啟用 client credentials 授權類型
```

### 8.2 網路連線問題
**情境**: 無法連線到 OAuth2 token 端點

**錯誤訊息**:
```
✗ 連線失敗
錯誤: 無法連線到 token URL
URL: https://oauth.example.com/token

疑難排解:
  • 驗證 token URL 是否正確
  • 檢查網路連線
  • 驗證防火牆/代理設定
  • 嘗試: curl -I https://oauth.example.com/token
```

### 8.3 檔案權限問題
**情境**: 無法寫入 Claude Code 設定

**錯誤訊息**:
```
✗ 配置更新失敗
錯誤: 權限被拒
檔案: ~/.claude/settings.json

疑難排解:
  • 檢查檔案權限: ls -la ~/.claude/settings.json
  • 修正權限: chmod 600 ~/.claude/settings.json
  • 確保您擁有該檔案: chown $USER ~/.claude/settings.json
```

### 8.4 損壞的配置
**情境**: 設定檔中有無效的 JSON

**錯誤訊息**:
```
✗ 配置讀取失敗
錯誤: 設定檔中有無效的 JSON
檔案: ~/.claude/settings.json
行: 42, 列: 15

疑難排解:
  • 從備份還原: gate-cli restore
  • 手動修正 JSON 語法
  • 檢視檔案: cat ~/.claude/settings.json
```

---

## 9. 測試策略

### 9.1 單元測試
**覆蓋率目標**: 最低 80%

**關鍵測試領域**:
- OAuth2Service: 測試 OAuth2 客戶端整合,模擬 token 取得流程
- ClaudeConfigService: 測試原子檔案操作和 JSON 序列化
- BackupService: 測試各種備份數量的輪替邏輯
- FileUtil: 測試原子寫入、權限處理
- ValidationUtil: 測試輸入驗證和錯誤處理

**測試範例**:
```java
@SpringBootTest
class OAuth2ServiceTest {

    @MockBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Autowired
    private OAuth2Service oauth2Service;

    @Test
    void testSuccessfulTokenAcquisition() {
        // 模擬成功的 OAuth2 token 取得
        OAuth2AccessToken mockToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "mock-token-value",
            Instant.now(),
            Instant.now().plus(Duration.ofHours(1))
        );

        OAuth2AuthorizedClient mockClient = mock(OAuth2AuthorizedClient.class);
        when(mockClient.getAccessToken()).thenReturn(mockToken);
        when(authorizedClientManager.authorize(any())).thenReturn(mockClient);

        // 驗證 token 解析和驗證
        OAuth2TokenResponse response = oauth2Service.getAccessToken("test-registration");
        assertNotNull(response);
        assertEquals("mock-token-value", response.getAccessToken());
    }

    @Test
    void testTokenValidation() {
        // 測試 token 過期驗證
        OAuth2AccessToken expiredToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "expired-token",
            Instant.now().minus(Duration.ofHours(2)),
            Instant.now().minus(Duration.ofHours(1))
        );

        assertFalse(oauth2Service.validateToken(expiredToken));
    }
}
```

### 9.2 整合測試
**測試情境**:
1. 使用模擬 OAuth2 伺服器的端對端連線流程
2. 備份建立和還原
3. 配置檔案損壞復原
4. Token 重新整理流程
5. 斷線和原始設定還原

### 9.3 手動測試檢查清單
- [ ] 使用有效憑證的首次連線
- [ ] 使用無效憑證的連線
- [ ] 重新連線 (覆寫現有配置)
- [ ] Token 過期前後的重新整理
- [ ] 備份列表和還原
- [ ] 斷線和設定清理
- [ ] Status 命令輸出準確性
- [ ] 檔案權限強制執行
- [ ] 跨平台相容性 (macOS、Linux、Windows)
- [ ] 所有命令的說明命令

---

## 10. 部署與發布

### 10.1 建構配置

**主要建構配置** (build.gradle):
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.8'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'org.graalvm.buildtools.native' version '0.10.6'
    id 'org.cyclonedx.bom' version '2.3.0'
}

group = 'io.github.samzhu'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

### 10.2 Spring Native 原生映像編譯

**主要發布方式**: 使用 GraalVM 編譯為原生可執行檔,**無需 Java 執行環境**

#### 10.2.1 編譯原生映像
```bash
# macOS / Linux
./gradlew nativeCompile

# Windows
gradlew.bat nativeCompile

# 編譯結果位於: build/native/nativeCompile/gate-cli
```

**編譯時間**: 首次編譯約 3-5 分鐘 (視硬體而定)

**產出檔案**:
- macOS: `gate-cli` (原生可執行檔,約 80-100 MB)
- Linux: `gate-cli` (原生可執行檔,約 80-100 MB)
- Windows: `gate-cli.exe` (原生可執行檔,約 80-100 MB)

#### 10.2.2 原生映像優勢
- **快速啟動**: < 100ms (相比 JVM 的 1-2 秒)
- **低記憶體**: 約 50-100 MB (相比 JVM 的 200-300 MB)
- **無需 Java**: 使用者不需要安裝 Java 執行環境
- **單一檔案**: 可執行檔包含所有依賴

#### 10.2.3 執行原生映像
```bash
# 直接執行
./build/native/nativeCompile/gate-cli connect --help

# 查看版本
./build/native/nativeCompile/gate-cli --version
```

### 10.3 安裝腳本

#### 10.3.1 Unix/Linux/macOS 安裝
```bash
#!/bin/bash
# install.sh
set -e

echo "正在安裝 Gate-CLI..."

# 建立安裝目錄
mkdir -p ~/.local/bin
mkdir -p ~/.gate-cli/backups

# 複製原生可執行檔
cp build/native/nativeCompile/gate-cli ~/.local/bin/

# 設定執行權限
chmod +x ~/.local/bin/gate-cli

# 加入 PATH (如果尚未加入)
if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
    echo "已將 ~/.local/bin 加入 PATH"
fi

echo "✓ Gate-CLI 安裝成功"
echo "請重新啟動 shell 或執行: source ~/.bashrc (或 ~/.zshrc)"
echo "然後執行: gate-cli --help"
```

#### 10.3.2 Windows 安裝
```powershell
# install.ps1
Write-Host "正在安裝 Gate-CLI..."

# 建立安裝目錄
$installDir = "$env:LOCALAPPDATA\gate-cli"
New-Item -ItemType Directory -Force -Path $installDir
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.gate-cli\backups"

# 複製可執行檔
Copy-Item "build\native\nativeCompile\gate-cli.exe" "$installDir\gate-cli.exe"

# 加入 PATH
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$installDir*") {
    [Environment]::SetEnvironmentVariable(
        "Path",
        "$userPath;$installDir",
        "User"
    )
    Write-Host "已將 $installDir 加入 PATH"
}

Write-Host "✓ Gate-CLI 安裝成功"
Write-Host "請重新開啟終端機,然後執行: gate-cli --help"
```


---

## 11. 安全考量

**設計原則**: Gate-CLI 設計為**本機使用工具**,專注於簡單性和易用性,安全性依賴作業系統層級保護。

### 11.1 憑證儲存 (簡化設計)

#### 11.1.1 儲存方式
- **明文儲存**: 配置檔案以明文 JSON 格式儲存客戶端憑證
- **檔案位置**: `~/.gate-cli/config.json`
- **權限建議**: 建議設定為 `600` (僅擁有者可讀寫)

```json
{
  "version": "1.0",
  "currentConnection": {
    "clientId": "client-id",
    "clientSecret": "client-secret-in-plaintext",
    "tokenUrl": "https://oauth.example.com/token",
    "apiUrl": "https://api.example.com/v1"
  }
}
```

#### 11.1.2 設計考量
- **為什麼不加密**:
  - 本機工具,加密金鑰仍需儲存在本機,無實質安全提升
  - 簡化實作,避免金鑰管理複雜性
  - 使用者可直接編輯配置檔案

- **安全建議**:
  - 依賴作業系統的檔案權限保護
  - 使用者層級隔離 (不同使用者無法存取)
  - 避免將配置檔案加入版本控制

### 11.2 OAuth2 基本安全

#### 11.2.1 連線安全
- **HTTPS 建議**: 強烈建議 token URL 使用 HTTPS
- **警告機制**: 如果使用 HTTP,顯示警告訊息
- **Token 驗證**: 驗證 token 過期時間,避免使用過期 token

#### 11.2.2 憑證輸入
```bash
# 方式 1: 命令列參數 (會留在 shell history)
gate-cli connect --client-id "xxx" --client-secret "yyy" ...

# 方式 2: 環境變數 (推薦,不留在 history)
export CLIENT_ID="xxx"
export CLIENT_SECRET="yyy"
gate-cli connect --client-id "$CLIENT_ID" --client-secret "$CLIENT_SECRET" ...

# 方式 3: 互動式輸入 (最安全,不留在 history)
gate-cli connect --client-id "xxx"
# 系統提示: Please enter client secret: [隱藏輸入]
```

### 11.3 檔案操作安全

#### 11.3.1 原子寫入
- **目的**: 防止崩潰時的部分寫入
- **實作**: 使用臨時檔案 + 原子重新命名

```java
Path tempFile = Files.createTempFile("settings", ".tmp");
Files.writeString(tempFile, jsonContent);
Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
```

#### 11.3.2 路徑驗證
- **防護**: 驗證所有檔案路徑,防止路徑遍歷攻擊
- **限制**: 僅允許操作 `~/.gate-cli/` 和 `~/.claude/` 目錄

#### 11.3.3 備份完整性
- **驗證**: 還原前驗證備份檔案是有效的 JSON
- **錯誤處理**: 如果備份損壞,提供清晰的錯誤訊息

### 11.4 日誌安全

#### 11.4.1 敏感資訊過濾
- **不記錄**: client secret、access token 完整內容
- **記錄**: client ID、token URL、API URL (非敏感)
- **部分顯示**: Token 前 10 字元 + "..." (除錯用)

```java
// 好的日誌
log.info("OAuth2 連線成功, client_id: {}", clientId);
log.debug("Token 取得成功: {}...", token.substring(0, 10));

// 不好的日誌 (避免)
log.info("Client secret: {}", clientSecret);  // ❌
log.info("Access token: {}", accessToken);    // ❌
```

### 11.5 使用者責任

#### 11.5.1 安全聲明
Gate-CLI 作為本機開發工具,安全性責任劃分:

**工具負責**:
- ✅ 提供基本的檔案權限建議
- ✅ 原子檔案操作防止資料損壞
- ✅ 日誌中過濾敏感資訊
- ✅ 提供 HTTPS 連線警告

**使用者負責**:
- 👤 設定適當的檔案權限 (`chmod 600`)
- 👤 保護自己的使用者帳戶和電腦
- 👤 不在共享環境中使用 (多人共用同一帳戶)
- 👤 定期更換 OAuth2 憑證
- 👤 不將配置檔案上傳到公開倉庫

#### 11.5.2 不適用場景
Gate-CLI **不適合**以下場景:
- ❌ 多使用者共享環境
- ❌ 生產環境的自動化腳本
- ❌ 需要企業級安全稽核
- ❌ 需要憑證加密儲存
- ❌ 需要符合特定合規要求 (如 PCI-DSS)

如有上述需求,請考慮企業級的密鑰管理解決方案。

### 11.6 未來安全增強 (選擇性)

如果未來有需求,可以考慮:
1. **加密儲存**: 使用系統金鑰鏈 (macOS Keychain / Windows Credential Manager / Linux Secret Service)
2. **Token 快取**: 避免重複儲存敏感資訊
3. **稽核日誌**: 記錄所有配置變更操作
4. **雙因素認證**: 支援 OAuth2 Authorization Code Flow

---

## 12. 監控與日誌

### 12.1 日誌級別
- **INFO**: 連線成功、配置變更、備份建立
- **WARN**: Token 即將過期、備份輪替、權限問題
- **ERROR**: OAuth2 失敗、檔案操作錯誤、驗證失敗
- **DEBUG**: 詳細的 OAuth2 請求、檔案操作、JSON 解析

### 12.2 日誌檔案位置
- **路徑**: `~/.gate-cli/gate-cli.log`
- **輪替**: 每日,保留 7 天
- **格式**: JSON 結構化日誌,便於解析

### 12.3 稽核追蹤
- 追蹤所有配置變更及時間戳記
- 記錄所有備份操作
- 記錄 token 重新整理事件
- 儲存在: `~/.gate-cli/audit.log`

---

## 13. 附錄

### 附錄 A: Spring Shell 3.4.1 使用的主要功能

**命令註冊**:
- 基於註解: `@Command`、`@CommandScan`、`@EnableCommand`
- 程式化: `CommandRegistration.builder()`

**選項處理**:
- `@Option` 搭配 `longNames`、`shortNames`、`required`
- 型別轉換和驗證
- 預設值

**命令可用性**:
- `@CommandAvailability` 搭配 `AvailabilityProvider`
- 基於狀態的命令啟用
- 使用者友善的不可用訊息

**說明系統**:
- 自動的 `--help` 和 `-h` 選項
- 透過描述自訂說明文字
- 群組組織

### 附錄 B: OAuth2 Client Credentials 流程

**請求格式**:
```http
POST /token HTTP/1.1
Host: oauth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_id=client-id
&client_secret=client-secret
&scope=api.read api.write
```

**回應格式**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api.read api.write"
}
```

### 附錄 C: Claude Code 設定結構

**最小設定**:
```json
{
  "apiKey": "sk-ant-...",
  "permissions": {
    "allow": ["*"],
    "deny": [],
    "ask": []
  }
}
```

**自訂端點配置**:
```json
{
  "apiKey": "not-used",
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "customEndpoint": "https://api.example.com/v1",
  "permissions": {
    "allow": ["*"],
    "deny": [],
    "ask": []
  }
}
```

### 附錄 D: 原子檔案寫入模式

**實作**:
```java
public void atomicWrite(String targetPath, String content) throws IOException {
    Path target = Paths.get(targetPath);
    Path temp = Paths.get(targetPath + ".tmp");

    // 以受限權限寫入臨時檔案
    Files.writeString(temp, content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);

    // 重新命名前設定權限
    Files.setPosixFilePermissions(temp,
        PosixFilePermissions.fromString("rw-------"));

    // 原子重新命名
    Files.move(temp, target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
}
```

---

## 文件核准

| 角色 | 姓名 | 日期 | 簽名 |
|------|------|------|------|
| 產品負責人 | | | |
| 技術主管 | | | |
| 安全審查者 | | | |

---

## 修訂歷史

| 版本 | 日期 | 作者 | 變更 |
|------|------|------|------|
| 1.0 | 2025-11-24 | AI 助理 | 初始 PRD 建立 |
