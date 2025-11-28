# Gate-CLI CI/CD 說明文件

## 文件資訊
- **版本**: 1.0.0
- **最後更新**: 2025-11-28
- **專案**: gate-cli

---

## 1. 概述

Gate-CLI 使用 GitHub Actions 實作持續整合與持續部署 (CI/CD)，包含兩個主要的 workflow：

| Workflow | 檔案 | 用途 |
|----------|------|------|
| CI | `.github/workflows/build.yml` | 每次程式碼變更時執行測試 |
| Release | `.github/workflows/release.yml` | 建置跨平台原生執行檔並發布 |

---

## 2. CI Workflow (build.yml)

### 2.1 觸發條件

```yaml
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
```

- **push to main**: 當程式碼推送到 main 分支時觸發
- **pull_request to main**: 當建立或更新 PR 到 main 分支時觸發

### 2.2 執行流程

```
┌─────────────────────────────────────────────────────────┐
│                    CI Workflow                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────┐         ┌─────────────┐                   │
│  │  test   │ ──────► │  build-jar  │                   │
│  └─────────┘         └─────────────┘                   │
│                                                         │
│  • 執行單元測試        • 建置 JAR 驗證編譯              │
│  • 上傳測試報告        • 上傳 JAR artifact              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 2.3 Job 說明

#### test
- **Runner**: `ubuntu-latest`
- **執行內容**:
  1. 設定 GraalVM (Java 25)
  2. 執行 `./gradlew test`
  3. 上傳測試報告 (保留 7 天)

#### build-jar
- **Runner**: `ubuntu-latest`
- **相依**: `test` 成功後執行
- **執行內容**:
  1. 執行 `./gradlew bootJar`
  2. 上傳 JAR artifact (保留 7 天)

### 2.4 預估執行時間

約 **3-5 分鐘**，提供快速的 CI 回饋。

---

## 3. Release Workflow (release.yml)

### 3.1 觸發條件

```yaml
on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:
    inputs:
      version:
        description: 'Release version (e.g., 0.0.1)'
        required: true
```

- **tag push**: 當推送 `v` 開頭的 tag 時觸發 (例如 `v1.0.0`, `v0.0.1-SNAPSHOT`)
- **手動觸發**: 透過 GitHub Actions UI 手動執行

### 3.2 執行流程

```
┌──────────────────────────────────────────────────────────────────────┐
│                       Release Workflow                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                    build-native (parallel)                       │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │ │
│  │  │  Linux   │ │  macOS   │ │  macOS   │ │ Windows  │           │ │
│  │  │   x64    │ │  Intel   │ │   ARM    │ │   x64    │           │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                              │                                        │
│                              ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                      create-release                              │ │
│  │  • 產生 CycloneDX SBOM                                          │ │
│  │  • 產生 SBOM HTML 報告 (Sunshine)                               │ │
│  │  • 產生 SBOM Attestation (供應鏈安全簽署)                        │ │
│  │  • 建立 GitHub Release                                          │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.3 GitHub-Hosted Runners

> **重要**: GraalVM native-image **不支援跨平台編譯**，必須在各目標平台上分別建置。

#### 可用的 Runners

| Runner Label | 作業系統 | 架構 | CPU | RAM |
|--------------|---------|------|-----|-----|
| **Linux** |
| `ubuntu-latest` | Ubuntu 24.04 | x64 | 4 | 16GB |
| `ubuntu-24.04-arm` | Ubuntu 24.04 | arm64 | 4 | 16GB |
| **macOS** |
| `macos-13` | macOS 13 | x64 (Intel) | 4 | 14GB |
| `macos-15-intel` | macOS 15 | x64 (Intel) | 4 | 14GB |
| `macos-latest` | macOS 15 | arm64 (Apple Silicon) | 3 | 7GB |
| `macos-14` | macOS 14 | arm64 (Apple Silicon) | 3 | 7GB |
| **Windows** |
| `windows-latest` | Windows Server 2025 | x64 | 4 | 16GB |
| `windows-11-arm` | Windows 11 | arm64 | 4 | 16GB |

> 參考: [GitHub-hosted runners](https://docs.github.com/en/actions/using-github-hosted-runners/using-github-hosted-runners/about-github-hosted-runners)

#### 目前啟用的建置矩陣

| 平台 | Runner | 產出檔案 |
|------|--------|---------|
| Linux x64 | `ubuntu-latest` | `gate-cli-linux-amd64.zip` |
| macOS Intel | `macos-13` | `gate-cli-macos-amd64.zip` |
| macOS Apple Silicon | `macos-latest` | `gate-cli-macos-arm64.zip` |
| Windows x64 | `windows-latest` | `gate-cli-windows-amd64.zip` |

#### 可選的建置 (已註解)

如需啟用，請編輯 `release.yml` 取消註解：

```yaml
# Linux ARM64
# - os: ubuntu-24.04-arm
#   artifact-name: gate-cli-linux-arm64
#   executable-name: gate-cli

# Windows ARM64
# - os: windows-11-arm
#   artifact-name: gate-cli-windows-arm64
#   executable-name: gate-cli.exe
```

### 3.4 SBOM (Software Bill of Materials)

#### 什麼是 SBOM？

SBOM 是軟體物料清單，記錄應用程式的所有相依套件，用於：
- 追蹤軟體供應鏈
- 識別已知漏洞
- 符合法規要求

#### 產生方式

1. **CycloneDX JSON**: 使用 `org.cyclonedx.bom` Gradle 插件
   ```bash
   ./gradlew cyclonedxBom
   # 產出: build/reports/application.cdx.json
   ```

2. **HTML 報告**: 使用 [CycloneDX Sunshine](https://github.com/CycloneDX/Sunshine) 工具
   - 互動式視覺化介面
   - 顯示相依套件、授權、漏洞資訊

#### SBOM Attestation (2025 最佳實踐)

使用 [actions/attest-sbom](https://github.com/actions/attest-sbom) 為 SBOM 產生簽署認證：

```yaml
- uses: actions/attest-sbom@v2
  with:
    subject-path: release/gate-cli-linux-amd64.zip
    sbom-path: release/gate-cli-sbom.cdx.json
```

**驗證 Attestation:**
```bash
gh attestation verify gate-cli-linux-amd64.zip \
  -R samzhu/gate-cli \
  --predicate-type https://cyclonedx.org/bom/v1.6
```

### 3.5 Release 產出

| 檔案 | 說明 |
|------|------|
| `gate-cli-linux-amd64.zip` | Linux x64 原生執行檔 |
| `gate-cli-macos-amd64.zip` | macOS Intel 原生執行檔 |
| `gate-cli-macos-arm64.zip` | macOS Apple Silicon 原生執行檔 |
| `gate-cli-windows-amd64.zip` | Windows x64 原生執行檔 |
| `gate-cli-sbom.cdx.json` | CycloneDX SBOM (JSON) |
| `gate-cli-sbom-report.html` | SBOM 互動式 HTML 報告 |

### 3.6 預估執行時間

約 **20-30 分鐘**，主要時間花在跨平台 native image 編譯。

---

## 4. 如何發布新版本

### 4.1 使用 Git Tag (建議)

```bash
# 1. 確保在 main 分支且程式碼最新
git checkout main
git pull origin main

# 2. 建立 tag
git tag v1.0.0

# 3. 推送 tag 到遠端 (自動觸發 Release workflow)
git push origin v1.0.0
```

### 4.2 手動觸發

1. 前往 GitHub 專案頁面
2. 點選 **Actions** 標籤
3. 選擇 **Release** workflow
4. 點選 **Run workflow**
5. 輸入版本號 (例如 `0.0.1`)
6. 點選 **Run workflow** 按鈕

### 4.3 版本命名慣例

| 格式 | 說明 | 範例 |
|------|------|------|
| `vX.Y.Z` | 正式版本 | `v1.0.0`, `v2.1.3` |
| `vX.Y.Z-SNAPSHOT` | 開發版本 | `v0.0.1-SNAPSHOT` |
| `vX.Y.Z-alpha.N` | Alpha 版本 | `v1.0.0-alpha.1` |
| `vX.Y.Z-beta.N` | Beta 版本 | `v1.0.0-beta.2` |
| `vX.Y.Z-rc.N` | Release Candidate | `v1.0.0-rc.1` |

> 包含 `SNAPSHOT`, `alpha`, `beta`, `rc` 的版本會自動標記為 **Pre-release**。

### 4.4 版本號處理邏輯

**Git Tag 為版本的唯一來源 (Single Source of Truth)**

```
┌─────────────────────────────────────────────────────────────┐
│  Git Tag: v1.0.0                                            │
│      │                                                      │
│      ▼                                                      │
│  VERSION=v1.0.0 (用於 GitHub Release 名稱)                  │
│      │                                                      │
│      ▼                                                      │
│  BUILD_VERSION=1.0.0 (移除 'v' 前綴，注入 Gradle)            │
│      │                                                      │
│      ▼                                                      │
│  ./gradlew nativeCompile -Pversion=1.0.0                    │
│      │                                                      │
│      ▼                                                      │
│  產出: gate-cli (內建版本 1.0.0)                             │
└─────────────────────────────────────────────────────────────┘
```

**build.gradle 設定：**

```gradle
// 版本可透過命令列覆蓋: ./gradlew build -Pversion=1.0.0
// CI/CD 會從 git tag 注入版本
version = findProperty('version') ?: '0.0.1-SNAPSHOT'
```

**本地開發 vs CI/CD：**

| 情境 | 版本來源 | 範例 |
|------|---------|------|
| 本地開發 | `build.gradle` 預設值 | `0.0.1-SNAPSHOT` |
| 本地指定版本 | 命令列參數 | `./gradlew build -Pversion=1.0.0` |
| CI/CD Release | Git Tag 自動注入 | Tag `v1.0.0` → 版本 `1.0.0` |

---

## 5. 所需權限

### 5.1 GitHub Token 權限

Release workflow 需要以下權限：

```yaml
permissions:
  contents: write      # 建立 Release
  attestations: write  # 產生 SBOM Attestation
  id-token: write      # OIDC token for Sigstore signing
  pages: write         # (可選) 部署到 GitHub Pages
```

### 5.2 Repository 設定

確保 Repository Settings → Actions → General：
- **Workflow permissions**: 選擇 "Read and write permissions"

---

## 6. 故障排除

### 6.1 Native Image 建置失敗

**問題**: GraalVM native-image 編譯錯誤

**解決方案**:
1. 檢查是否有反射、序列化等需要額外設定的程式碼
2. 確認 `src/main/resources/META-INF/native-image/` 設定檔正確
3. 參考 [GraalVM Native Image Compatibility](https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/)

### 6.2 SBOM 產生失敗

**問題**: CycloneDX plugin 執行錯誤

**解決方案**:
1. 確認 `build.gradle` 包含 `id 'org.cyclonedx.bom' version '2.3.0'`
2. 執行 `./gradlew cyclonedxBom --info` 查看詳細錯誤

### 6.3 Attestation 驗證失敗

**問題**: `gh attestation verify` 回報錯誤

**解決方案**:
1. 確認 Repository 為 public (私有 repo 使用 GitHub 內部 Sigstore)
2. 確認使用正確的 `--predicate-type`
3. 檢查 artifact 是否被修改過

### 6.4 Windows 建置錯誤: Task '.0.1' not found

**問題**: 在 Windows runner 執行 `./gradlew test -Pversion=0.0.1` 時出現：

```
Task '.0.1' not found in root project 'gate-cli'.
```

**原因**: 這是 **PowerShell 解析等號 (`=`) 的已知問題**。

Windows GitHub Actions runner 預設使用 PowerShell (pwsh) 作為 shell。PowerShell 在解析包含 `=` 的參數時會出現問題，導致 `-Pversion=0.0.1` 被錯誤解析：
- PowerShell 將 `version=0` 部分消耗掉
- 剩餘的 `.0.1` 被 Gradle 誤認為 task 名稱

**解決方案**: 明確指定使用 `bash` shell

```yaml
# ❌ 錯誤：未指定 shell，Windows 會使用 PowerShell
- name: Run tests
  run: ./gradlew test -Pversion=${{ env.BUILD_VERSION }}

# ✅ 正確：明確指定 shell: bash
- name: Run tests
  shell: bash
  run: ./gradlew test -Pversion=${{ env.BUILD_VERSION }}
```

**替代方案**:

| 方案 | 指令 | 說明 |
|------|------|------|
| `shell: bash` ✅ | `./gradlew test -Pversion=1.0.0` | 推薦，跨平台一致 |
| `shell: cmd` | `gradlew.bat test -Pversion=1.0.0` | Windows 原生 CMD |
| 引號包裹 | `./gradlew test "-Pversion=1.0.0"` | PowerShell 需小心處理 |

**參考資料**:
- [PowerShell about_Parsing](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_parsing)
- [GitHub Actions - All the Shells](https://dev.to/pwd9000/github-actions-all-the-shells-581h)

---

## 7. 參考資源

- [GitHub Actions 文件](https://docs.github.com/en/actions)
- [GraalVM setup-graalvm Action](https://github.com/graalvm/setup-graalvm)
- [GitHub-hosted Runners](https://docs.github.com/en/actions/using-github-hosted-runners/using-github-hosted-runners/about-github-hosted-runners)
- [actions/attest-sbom](https://github.com/actions/attest-sbom)
- [CycloneDX](https://cyclonedx.org/)
- [softprops/action-gh-release](https://github.com/softprops/action-gh-release)

---

## 修訂歷史

| 版本 | 日期 | 作者 | 變更 |
|------|------|------|------|
| 1.0.0 | 2025-11-28 | AI 助理 | 初始文件建立 |
| 1.0.1 | 2025-11-28 | AI 助理 | 新增 6.4 Windows PowerShell 參數解析問題說明 |
