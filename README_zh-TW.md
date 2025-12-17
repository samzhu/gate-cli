# Gate-CLI 1.0.0

[English](README.md)

Gate-CLI 是一個命令列工具，用於自動化配置 Claude Code 以使用受 OAuth2 認證保護的自訂 API 端點。

## 亮點

- **雙認證模式** - 支援互動式瀏覽器登入 (PKCE) 和機器對機器 (Client Credentials) 認證流程
- **原生執行檔** - 使用 GraalVM 建構，啟動時間低於 100ms，無需 Java 執行環境
- **企業就緒** - 專為企業部署的自訂 OAuth2 端點設計
- **安全操作** - 原子檔案寫入和自動備份輪替，防止配置損壞

---

## 下載

| 平台 | 架構 | 檔案 |
|------|------|------|
| Linux | x64 | `gate-cli-linux-amd64.zip` |
| macOS | Intel (x64) | `gate-cli-macos-amd64.zip` |
| macOS | Apple Silicon (ARM64) | `gate-cli-macos-arm64.zip` |
| Windows | x64 | `gate-cli-windows-amd64.zip` |

## 安裝

1. 下載適合您平台的 ZIP 檔案
2. 解壓縮：`unzip gate-cli-<platform>.zip`
3. (macOS/Linux) 設定執行權限：`chmod +x gate-cli`
4. **(僅 macOS)** 移除隔離屬性：`xattr -d com.apple.quarantine gate-cli`
5. 直接執行：`./gate-cli` 或加入 PATH：`mv gate-cli /usr/local/bin/`

> **macOS 使用者注意：** macOS Gatekeeper 可能會阻擋執行檔，因為它沒有 Apple 開發者憑證簽名。執行 `xattr` 指令可移除隔離標記，允許執行檔正常運作。

---

## 快速開始

### PKCE 流程（互動式登入）

```bash
# 1. 設定配置
gate-cli config \
  --client-id "your-client-id" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"

# 2. 登入（開啟瀏覽器）
gate-cli login

# 3. 檢查狀態
gate-cli status

# 4. 登出
gate-cli logout
```

### Client Credentials 流程（M2M）

```bash
# 1. 設定配置（含 secret）
gate-cli config \
  --client-id "your-client-id" \
  --client-secret "your-client-secret" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"

# 2. 連線
gate-cli connect

# 3. 重新整理 Token
gate-cli refresh

# 4. 斷線
gate-cli disconnect
```

---

## 命令

### 認證

| 命令 | 說明 |
|------|------|
| `login` | OAuth2 PKCE 流程（瀏覽器登入，用於互動式使用者） |
| `connect` | OAuth2 Client Credentials 流程（M2M，用於自動化） |
| `logout` / `disconnect` | 斷線並還原原始 Claude Code 設定 |
| `refresh` | 重新整理存取權杖（僅限 Client Credentials） |

### 配置

| 命令 | 說明 |
|------|------|
| `config` | 管理設定 (client-id, client-secret, issuer-uri, api-url) |
| `config --list` | 顯示目前配置 |
| `config --reset` | 重置所有設定 |
| `status` | 顯示連線狀態和配置 |

### 備份與還原

| 命令 | 說明 |
|------|------|
| `restore` | 從最近的備份還原 Claude Code 設定 |
| `restore --list` | 列出所有可用備份 |
| `restore --backup <path>` | 從指定備份檔案還原 |

---

## 核心功能

### OIDC Discovery
透過 `/.well-known/openid-configuration` 從 issuer URI 自動探索 OAuth2 端點。

### 自動備份輪替
- 維護最多 10 個時間戳記備份
- 保留原始設定 (`settings.json.original`) 以便完整還原
- 超過限制時自動清理最舊的備份

### 原子檔案操作
所有寫入使用臨時檔案 + 原子重新命名模式，防止配置損壞。

### 安全性
- 在輸出中遮蔽敏感資訊
- 從日誌中過濾敏感資訊
- 建議設定 `chmod 600` 保護配置檔案
- 使用 HTTP（非 HTTPS）時發出警告

---

## 配置檔案

| 檔案 | 說明 |
|------|------|
| `~/.gate-cli/config.json` | Gate-CLI 設定和連線狀態 |
| `~/.gate-cli/backups/` | 備份目錄（自動輪替） |
| `~/.claude/settings.json` | Claude Code 設定（由 gate-cli 管理） |

---

## OAuth2 需求

### PKCE 流程 (login)
- 回呼 URL：`http://localhost:8080/callback`（需在 OAuth2 提供者註冊）
- 必要設定：`client-id`、`issuer-uri`、`api-url`

### Client Credentials 流程 (connect)
- 必要設定：`client-id`、`client-secret`、`issuer-uri`、`api-url`
- 在 OAuth2 伺服器上啟用 `client_credentials` 授權類型

---

## 技術堆疊

| 元件 | 版本 |
|------|------|
| Spring Boot | 3.5.8 |
| Spring Shell | 3.4.1 |
| Java | 25 |
| GraalVM Native | 0.10.6 |

---

## 已知限制

- 憑證以明文儲存（專為本機開發設計）
- 不適用於共享/多使用者環境
- PKCE 流程需要桌面瀏覽器
- Windows 執行檔需要 Visual C++ Redistributable

---

## SBOM（軟體物料清單）

為確保供應鏈安全，提供 SBOM 檔案：
- `gate-cli-sbom.cdx.json` - CycloneDX 格式（機器可讀）
- `gate-cli-sbom-report.html` - 互動式 HTML 報告

**驗證 SBOM 證明：**
```bash
gh attestation verify gate-cli-<platform>.zip \
  -R samzhu/gate-cli \
  --predicate-type https://cyclonedx.org/bom
```

---

## 文件

- [README](https://github.com/samzhu/gate-cli/blob/main/README.md) - 完整使用指南（英文）
- [README (繁體中文)](https://github.com/samzhu/gate-cli/blob/main/README_zh-TW.md) - 繁體中文文件

---

## 授權

MIT License