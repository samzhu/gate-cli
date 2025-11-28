# Gate-CLI

[English](README.md)

**簡化 Claude Code 與 OAuth2 保護的自訂 API 端點配置**

Gate-CLI 是一個命令列工具，用於自動化配置 Claude Code 以使用受 OAuth2 認證保護的自訂 API 端點。

## 功能特色

- **兩種認證模式**
  - `login` - OAuth2 PKCE 流程（瀏覽器登入，適用於互動式使用者）
  - `connect` - OAuth2 Client Credentials 流程（M2M，適用於自動化）
- **統一配置管理** - 所有設定透過 `config` 命令管理
- **OIDC Discovery** - 從 issuer URI 自動探索 OAuth2 端點
- **備份與還原** - 自動備份輪替（保留最近 10 個備份）
- **快速啟動** - 使用 GraalVM 編譯，啟動時間低於 100ms
- **原子操作** - 使用原子檔案寫入防止配置損壞
- **企業就緒** - 專為企業部署的自訂 OAuth2 端點設計

## 系統需求

- Java 25+（用於開發和執行 JAR）
- GraalVM（用於原生編譯）
- 已安裝 Claude Code

## 安裝

### 選項 1：從 JAR 執行

```bash
# 建構專案
./gradlew clean build

# 執行 CLI
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar
```

### 選項 2：原生編譯

```bash
# 建構原生映像
./gradlew nativeCompile -x test

# 執行原生可執行檔
./build/native/nativeCompile/gate-cli
```

## 快速開始

Gate-CLI 支援兩種認證模式：
- **PKCE 流程**（推薦）- 透過瀏覽器互動式登入，適合一般使用者
- **Client Credentials 流程** - M2M 自動化認證，適合服務帳戶

以下以 **PKCE 流程** 為例說明。

### 1. 配置設定

```bash
gate-cli config \
  --client-id "your-client-id" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"
```

**參數說明：**

| 參數 | 說明 |
|------|------|
| `--client-id` | OAuth2 Client ID，由您的 OAuth2 提供者核發 |
| `--issuer-uri` | OIDC Issuer URI，用於自動探索 OAuth2 端點（authorization、token endpoint） |
| `--api-url` | 自訂 Claude API 端點 URL，Gate-CLI 會將此寫入 Claude Code 設定 |

### 2. 登入

```bash
gate-cli login
```

執行後會：
1. 從 `issuer-uri` 探索 OIDC 端點（`/.well-known/openid-configuration`）
2. 產生 PKCE code_verifier 和 code_challenge
3. 開啟瀏覽器導向授權頁面
4. 啟動本地伺服器等待回調（`http://localhost:8080/callback`）
5. 使用者完成登入授權後，取得 authorization code
6. 交換 access token
7. 將 token 寫入 Claude Code 設定（`~/.claude/settings.json`）

> **注意：** 請確保在 OAuth2 提供者中已註冊回調 URL：`http://localhost:8080/callback`

### 3. 檢查狀態

```bash
gate-cli status
```

輸出：
```
Gate-CLI Status
===============

Configuration:
  Client ID:     your-client-id
  Client Secret: (not set)
  Issuer URI:    https://auth.example.com/
  API URL:       https://api.example.com/v1

Connection:
  Status: Connected
  Auth Type: OAuth2 PKCE (Public Client)
  Token Status: Valid (expires in 55 minutes)
  Last Connected: 2025-11-28 10:30:45

Available Backups: 3

Claude Code Settings:
  Location: ~/.claude/settings.json
  Last Modified: 2025-11-28 10:30:46
```

### 4. 登出

```bash
gate-cli logout
```

這會還原您原始的 Claude Code 設定。

---

## M2M 模式（Client Credentials 流程）

若需要自動化或服務帳戶認證，可使用 Client Credentials 流程：

### 1. 配置設定（包含 client-secret）

```bash
gate-cli config \
  --client-id "your-client-id" \
  --client-secret "your-client-secret" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"
```

### 2. 連線

```bash
gate-cli connect
```

### 3. 重新整理 Token

```bash
gate-cli refresh
```

### 4. 斷線

```bash
gate-cli disconnect
```

---

## 命令參考

### 配置管理

#### `config`

管理 gate-cli 配置設定。

**選項：**
- `-i, --client-id` - 設定 OAuth2 client ID
- `-s, --client-secret` - 設定 OAuth2 client secret（用於 M2M）
- `-u, --issuer-uri` - 設定 OAuth2 issuer URI（用於 OIDC discovery）
- `-a, --api-url` - 設定 Claude API 端點 URL
- `-l, --list` - 列出目前配置
- `-r, --reset` - 重置所有設定

**範例：**
```bash
# 設定個別設定
gate-cli config --client-id "my-client-id"
gate-cli config --client-secret "my-secret"
gate-cli config --issuer-uri "https://auth.example.com/"
gate-cli config --api-url "https://api.example.com/v1"

# 一次設定多個設定
gate-cli config \
  --client-id "my-client-id" \
  --client-secret "my-secret" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"

# 檢視目前配置
gate-cli config
gate-cli config --list

# 重置所有設定
gate-cli config --reset
```

#### `status`

顯示目前連線狀態和配置。

```bash
gate-cli status
```

### 連線管理

#### `login`

透過 OAuth2 瀏覽器流程搭配 PKCE 登入（適用於互動式使用者）。

**必要配置：**
- `client-id`
- `issuer-uri`
- `api-url`

**回調 URL：** `http://localhost:8080/callback`

> **注意：** 請確保在您的 OAuth2 提供者中註冊此回調 URL。

```bash
gate-cli login
```

#### `connect`

使用 OAuth2 Client Credentials 流程連線（適用於 M2M/自動化）。

**必要配置：**
- `client-id`
- `client-secret`
- `issuer-uri`
- `api-url`

```bash
gate-cli connect
```

#### `logout` / `disconnect`

登出並還原原始 Claude Code 設定。兩個命令等效。

```bash
gate-cli logout
# 或
gate-cli disconnect
```

#### `refresh`

使用儲存的憑證重新整理存取權杖。

> **注意：** 僅在透過 `connect` 命令（Client Credentials 流程）連線時可用。

```bash
gate-cli refresh
```

### 備份管理

#### `restore`

從備份還原 Claude Code 設定。

**選項：**
- `-b, --backup` - 指定要還原的備份檔案
- `-l, --list` - 列出可用備份

```bash
# 列出所有備份
gate-cli restore --list

# 還原最近的備份
gate-cli restore

# 還原指定備份
gate-cli restore --backup ~/.gate-cli/backups/settings.json.backup.2025-11-28-10-00-00
```

---

## 配置檔案

### Gate-CLI 配置

**位置：** `~/.gate-cli/config.json`

```json
{
  "version": "3.0",
  "settings": {
    "clientId": "your-client-id",
    "clientSecret": "your-client-secret",
    "issuerUri": "https://auth.example.com/",
    "apiUrl": "https://api.example.com/v1"
  },
  "currentConnection": {
    "authType": "pkce",
    "clientId": "your-client-id",
    "issuerUri": "https://auth.example.com/",
    "apiUrl": "https://api.example.com/v1",
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

### Claude Code 設定

**位置：** `~/.claude/settings.json`

Gate-CLI 修改後會包含：
```json
{
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "your-access-token",
    "ANTHROPIC_BASE_URL": "https://api.example.com/v1"
  }
}
```

### 備份

**位置：** `~/.gate-cli/backups/`

- 自動輪替（保留最近 10 個備份）
- 以時間戳記命名：`settings.json.backup.YYYY-MM-DD-HH-mm-ss`
- 原始備份永久保留：`settings.json.original`

---

## 執行模式

Gate-CLI 支援兩種執行模式。詳見 [Spring Shell 執行文件](https://docs.spring.io/spring-shell/reference/execution.html)。

### 非互動模式（單一命令）

直接執行命令後退出：

```bash
gate-cli config --client-id "my-client"
gate-cli login
gate-cli status
gate-cli logout
```

### 互動模式（Shell）

啟動互動式 shell 執行多個命令：

```bash
gate-cli
```

然後在提示符下輸入命令：

```
gate-cli:>config --client-id "my-client"
gate-cli:>login
gate-cli:>status
gate-cli:>exit
```

---

## 安全考量

### 本機工具設計

Gate-CLI 設計為**本機開發工具**。安全性依賴作業系統層級的檔案權限。

**Gate-CLI 負責：**
- ✅ 建議檔案權限（`chmod 600`）
- ✅ 原子檔案操作防止損壞
- ✅ 過濾日誌中的敏感資訊
- ✅ 在顯示輸出中遮罩密鑰

**使用者需負責：**
- 👤 設定適當的檔案權限
- 👤 保護使用者帳戶和電腦
- 👤 不在共享環境中使用
- 👤 定期更換 OAuth2 憑證
- 👤 不將配置檔案提交到版本控制

**憑證儲存：**
- 以明文儲存於 `~/.gate-cli/config.json`
- 依賴作業系統檔案權限保護
- 為本機 CLI 工具使用簡化的設計

**不適用於：**
- ❌ 多使用者共享環境
- ❌ 生產環境自動化腳本
- ❌ 企業級安全稽核
- ❌ 合規要求（PCI-DSS 等）

---

## 疑難排解

### 缺少配置

```
✗ Missing configuration: client-id
Use 'config --client-id <id>' to set it.
```

**解決方案：** 使用 `config` 命令設定必要配置。

### OAuth2 認證失敗

```
✗ OAuth2 authentication failed
Error: invalid_client
```

**解決方案：**
- 確認 client ID 和 secret 正確
- 檢查 client 是否已在 OAuth2 伺服器註冊
- 確保已啟用正確的授權類型（PKCE 或 Client Credentials）

### OIDC Discovery 失敗

```
✗ Failed to discover OIDC configuration
```

**解決方案：**
- 確認 issuer URI 正確
- 檢查 `/.well-known/openid-configuration` 是否可存取
- 測試：`curl https://auth.example.com/.well-known/openid-configuration`

### 回調 URL 未註冊

若 `login` 因重新導向錯誤失敗：

**解決方案：** 在您的 OAuth2 提供者中將 `http://localhost:8080/callback` 註冊為允許的重新導向 URI。

### 權限被拒

```
✗ Configuration update failed
Error: Permission denied
```

**解決方案：**
- 檢查檔案權限：`ls -la ~/.claude/settings.json`
- 修正權限：`chmod 600 ~/.claude/settings.json`
- 確保檔案擁有權：`chown $USER ~/.claude/settings.json`

### 完全重置

若遇到無法解決的問題，可以完全重置 Gate-CLI 和 Claude Code 設定：

**影響範圍：**

| 路徑 | 說明 |
|------|------|
| `~/.claude/settings.json` | Claude Code 設定檔（Gate-CLI 寫入的 token 和 API URL） |
| `~/.gate-cli/` | Gate-CLI 配置目錄（包含 config.json 和備份） |

**重置腳本（macOS / Linux）：**

```bash
#!/bin/bash
# Gate-CLI 完全重置腳本

echo "⚠️  這將刪除所有 Gate-CLI 配置和 Claude Code 設定"
read -p "確定要繼續嗎？(y/N) " confirm

if [[ "$confirm" =~ ^[Yy]$ ]]; then
    # 刪除 Claude Code 設定
    if [ -f ~/.claude/settings.json ]; then
        rm ~/.claude/settings.json
        echo "✓ 已刪除 ~/.claude/settings.json"
    else
        echo "- ~/.claude/settings.json 不存在"
    fi

    # 刪除 Gate-CLI 配置目錄
    if [ -d ~/.gate-cli ]; then
        rm -rf ~/.gate-cli
        echo "✓ 已刪除 ~/.gate-cli/"
    else
        echo "- ~/.gate-cli/ 不存在"
    fi

    echo ""
    echo "✓ 重置完成"
else
    echo "已取消"
fi
```

**快速重置（單行命令）：**

```bash
# 刪除所有 Gate-CLI 相關檔案
rm -f ~/.claude/settings.json && rm -rf ~/.gate-cli && echo "✓ 重置完成"
```

> **注意：** 重置後需要重新執行 `gate-cli config` 設定配置，再執行 `gate-cli login` 或 `gate-cli connect`。

---

## 開發

### 建構

```bash
# 建構 JAR
./gradlew clean build

# 跳過測試
./gradlew clean build -x test

# 執行測試
./gradlew test
```

### 執行

```bash
# 從 JAR 執行（互動模式）
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar

# 從 JAR 執行（非互動模式）
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar status

# 使用 Gradle 執行
./gradlew bootRun
```

### 原生編譯

```bash
# 建構原生映像
./gradlew nativeCompile

# 執行原生可執行檔
./build/native/nativeCompile/gate-cli
```

---

## 技術堆疊

- **Spring Boot** 3.5.8
- **Spring Shell** 3.4.1（使用新的 `@Command` 註解模型）
- **Java** 25
- **GraalVM Native** 0.10.6
- **Jackson** 2.x（JSON 處理）
- **Lombok**（減少樣板程式碼）

---

## 授權

本專案採用 MIT 授權條款。

## 貢獻

歡迎貢獻！請隨時提交 Pull Request。

## 作者

Sam Zhu ([@samzhu](https://github.com/samzhu))

## 致謝

- 使用 [Spring Shell](https://spring.io/projects/spring-shell) 建構
- OAuth2 支援由 [Spring Security OAuth2 Client](https://spring.io/projects/spring-security-oauth) 提供
- 原生編譯使用 [GraalVM](https://www.graalvm.org/)
