# Gate-CLI 1.0.0

[繁體中文版](README_zh-TW.md)

Gate-CLI is a command-line tool for automating Claude Code configuration with OAuth2-protected custom API endpoints.

## Highlights

- **Two Authentication Modes** - Interactive browser login (PKCE) and machine-to-machine (Client Credentials) flows
- **Native Executable** - Built with GraalVM for sub-100ms startup, no Java runtime required
- **Enterprise Ready** - Designed for custom OAuth2 endpoints in enterprise deployments
- **Safe Operations** - Atomic file writes and automatic backup rotation prevent configuration corruption

---

## Downloads

| Platform | Architecture | File |
|----------|--------------|------|
| Linux | x64 | `gate-cli-linux-amd64.zip` |
| macOS | Intel (x64) | `gate-cli-macos-amd64.zip` |
| macOS | Apple Silicon (ARM64) | `gate-cli-macos-arm64.zip` |
| Windows | x64 | `gate-cli-windows-amd64.zip` |

## Installation

1. Download the appropriate ZIP file for your platform
2. Extract: `unzip gate-cli-<platform>.zip`
3. (macOS/Linux) Make executable: `chmod +x gate-cli`
4. **(macOS only)** Remove quarantine attribute: `xattr -d com.apple.quarantine gate-cli`
5. Run directly: `./gate-cli` or add to PATH: `mv gate-cli /usr/local/bin/`

> **Note for macOS users:** macOS Gatekeeper may block the executable because it's not signed with an Apple Developer certificate. Running the `xattr` command removes the quarantine flag and allows the binary to execute.

---

## Quick Start

### PKCE Flow (Interactive Login)

```bash
# 1. Configure
gate-cli config \
  --client-id "your-client-id" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"

# 2. Login (opens browser)
gate-cli login

# 3. Check status
gate-cli status

# 4. Logout
gate-cli logout
```

### Client Credentials Flow (M2M)

```bash
# 1. Configure (with secret)
gate-cli config \
  --client-id "your-client-id" \
  --client-secret "your-client-secret" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"

# 2. Connect
gate-cli connect

# 3. Refresh token
gate-cli refresh

# 4. Disconnect
gate-cli disconnect
```

---

## Commands

### Authentication

| Command | Description |
|---------|-------------|
| `login` | OAuth2 PKCE flow (browser-based, for interactive users) |
| `connect` | OAuth2 Client Credentials flow (M2M, for automation) |
| `logout` / `disconnect` | Disconnect and restore original Claude Code settings |
| `refresh` | Refresh access token (Client Credentials only) |

### Configuration

| Command | Description |
|---------|-------------|
| `config` | Manage settings (client-id, client-secret, issuer-uri, api-url) |
| `config --list` | Show current configuration |
| `config --reset` | Reset all settings |
| `status` | Show connection status and configuration |

### Backup & Restore

| Command | Description |
|---------|-------------|
| `restore` | Restore Claude Code settings from most recent backup |
| `restore --list` | List all available backups |
| `restore --backup <path>` | Restore from specific backup file |

---

## Key Features

### OIDC Discovery
Automatically discovers OAuth2 endpoints from issuer URI via `/.well-known/openid-configuration`.

### Automatic Backup Rotation
- Maintains up to 10 timestamped backups
- Preserves original settings (`settings.json.original`) for clean disconnect
- Auto-cleanup of oldest backups when limit exceeded

### Atomic File Operations
All writes use temp file + atomic rename pattern to prevent corruption.

### Security
- Secrets masked in output
- Sensitive info filtered from logs
- Recommends `chmod 600` for config files
- Warns on HTTP (non-HTTPS) usage

---

## Configuration Files

| File | Description |
|------|-------------|
| `~/.gate-cli/config.json` | Gate-CLI settings and connection state |
| `~/.gate-cli/backups/` | Backup directory (auto-rotation) |
| `~/.claude/settings.json` | Claude Code settings (managed by gate-cli) |

---

## OAuth2 Requirements

### PKCE Flow (login)
- Callback URL: `http://localhost:8080/callback` (register in OAuth2 provider)
- Required: `client-id`, `issuer-uri`, `api-url`

### Client Credentials Flow (connect)
- Required: `client-id`, `client-secret`, `issuer-uri`, `api-url`
- Enable `client_credentials` grant type on OAuth2 server

---

## Technology Stack

| Component | Version |
|-----------|---------|
| Spring Boot | 3.5.8 |
| Spring Shell | 3.4.1 |
| Java | 25 |
| GraalVM Native | 0.10.6 |

---

## Known Limitations

- Credentials stored in plaintext (designed for local development)
- Not suitable for shared/multi-user environments
- PKCE flow requires desktop browser
- Windows binary requires Visual C++ Redistributable

---

## SBOM (Software Bill of Materials)

For supply chain security, SBOM files are provided:
- `gate-cli-sbom.cdx.json` - CycloneDX format (machine-readable)
- `gate-cli-sbom-report.html` - Interactive HTML report

**Verify SBOM Attestation:**
```bash
gh attestation verify gate-cli-<platform>.zip \
  -R samzhu/gate-cli \
  --predicate-type https://cyclonedx.org/bom
```

---

## Documentation

- [README](https://github.com/samzhu/gate-cli/blob/main/README.md) - Complete usage guide
- [README (繁體中文)](https://github.com/samzhu/gate-cli/blob/main/README_zh-TW.md) - Chinese documentation

---

## License

MIT License