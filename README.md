# Gate-CLI

[繁體中文版](README_zh-TW.md)

**Simplify Claude Code configuration with OAuth2-protected custom API endpoints**

Gate-CLI is a command-line tool that automates the configuration of Claude Code to use custom API endpoints protected by OAuth2 authentication.

## Features

- **Two Authentication Modes**
  - `login` - OAuth2 PKCE flow (browser-based, for interactive users)
  - `connect` - OAuth2 Client Credentials flow (M2M, for automation)
- **Unified Configuration** - All settings managed via `config` command
- **OIDC Discovery** - Automatically discovers OAuth2 endpoints from issuer URI
- **Backup & Restore** - Automatic backup rotation (keeps last 10 backups)
- **Fast Startup** - Built with GraalVM for sub-100ms startup time
- **Atomic Operations** - Prevents configuration corruption with atomic file writes
- **Enterprise Ready** - Designed for custom OAuth2 endpoints in enterprise deployments

## Prerequisites

- Java 25+ (for development and running JAR)
- GraalVM (for native compilation)
- Claude Code installed

## Installation

### Option 1: Run from JAR

```bash
# Build the project
./gradlew clean build

# Run the CLI
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar
```

### Option 2: Native Compilation

```bash
# Build native image
./gradlew nativeCompile -x test

# Run native executable
./build/native/nativeCompile/gate-cli
```

## Quick Start

Gate-CLI supports two authentication modes:
- **PKCE Flow** (Recommended) - Interactive browser-based login for regular users
- **Client Credentials Flow** - M2M automated authentication for service accounts

The following example demonstrates the **PKCE Flow**.

### 1. Configure Settings

```bash
gate-cli config \
  --client-id "your-client-id" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"
```

**Parameter Description:**

| Parameter | Description |
|-----------|-------------|
| `--client-id` | OAuth2 Client ID, issued by your OAuth2 provider |
| `--issuer-uri` | OIDC Issuer URI, used to auto-discover OAuth2 endpoints (authorization, token endpoint) |
| `--api-url` | Custom Claude API endpoint URL, Gate-CLI will write this to Claude Code settings |

### 2. Login

```bash
gate-cli login
```

This will:
1. Discover OIDC endpoints from `issuer-uri` (`/.well-known/openid-configuration`)
2. Generate PKCE code_verifier and code_challenge
3. Open browser to authorization page
4. Start local server waiting for callback (`http://localhost:8080/callback`)
5. After user completes login, obtain authorization code
6. Exchange for access token
7. Write token to Claude Code settings (`~/.claude/settings.json`)

> **Note:** Make sure to register the callback URL in your OAuth2 provider: `http://localhost:8080/callback`

### 3. Check Status

```bash
gate-cli status
```

Output:
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

### 4. Logout

```bash
gate-cli logout
```

This restores your original Claude Code settings.

---

## M2M Mode (Client Credentials Flow)

For automation or service account authentication, use the Client Credentials flow:

### 1. Configure Settings (including client-secret)

```bash
gate-cli config \
  --client-id "your-client-id" \
  --client-secret "your-client-secret" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"
```

### 2. Connect

```bash
gate-cli connect
```

### 3. Refresh Token

```bash
gate-cli refresh
```

### 4. Disconnect

```bash
gate-cli disconnect
```

---

## Commands Reference

### Configuration Management

#### `config`

Manage gate-cli configuration settings.

**Options:**
- `-i, --client-id` - Set OAuth2 client ID
- `-s, --client-secret` - Set OAuth2 client secret (for M2M)
- `-u, --issuer-uri` - Set OAuth2 issuer URI (for OIDC discovery)
- `-a, --api-url` - Set Claude API endpoint URL
- `-l, --list` - List current configuration
- `-r, --reset` - Reset all settings

**Examples:**
```bash
# Set individual settings
gate-cli config --client-id "my-client-id"
gate-cli config --client-secret "my-secret"
gate-cli config --issuer-uri "https://auth.example.com/"
gate-cli config --api-url "https://api.example.com/v1"

# Set multiple settings at once
gate-cli config \
  --client-id "my-client-id" \
  --client-secret "my-secret" \
  --issuer-uri "https://auth.example.com/" \
  --api-url "https://api.example.com/v1"

# View current configuration
gate-cli config
gate-cli config --list

# Reset all settings
gate-cli config --reset
```

#### `status`

Show current connection status and configuration.

```bash
gate-cli status
```

### Connection Management

#### `login`

Login via OAuth2 browser flow with PKCE (for interactive users).

**Required Configuration:**
- `client-id`
- `issuer-uri`
- `api-url`

**Callback URL:** `http://localhost:8080/callback`

> **Note:** Make sure to register this callback URL in your OAuth2 provider.

```bash
gate-cli login
```

#### `connect`

Connect using OAuth2 Client Credentials flow (for M2M/automation).

**Required Configuration:**
- `client-id`
- `client-secret`
- `issuer-uri`
- `api-url`

```bash
gate-cli connect
```

#### `logout` / `disconnect`

Logout and restore original Claude Code settings. Both commands are equivalent.

```bash
gate-cli logout
# or
gate-cli disconnect
```

#### `refresh`

Refresh access token using stored credentials.

> **Note:** Only available when connected via `connect` command (Client Credentials flow).

```bash
gate-cli refresh
```

### Backup Management

#### `restore`

Restore Claude Code settings from a backup.

**Options:**
- `-b, --backup` - Specific backup file to restore
- `-l, --list` - List available backups

```bash
# List all backups
gate-cli restore --list

# Restore most recent backup
gate-cli restore

# Restore specific backup
gate-cli restore --backup ~/.gate-cli/backups/settings.json.backup.2025-11-28-10-00-00
```

---

## Configuration Files

### Gate-CLI Configuration

**Location:** `~/.gate-cli/config.json`

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

### Claude Code Settings

**Location:** `~/.claude/settings.json`

Modified by Gate-CLI to include:
```json
{
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "your-access-token",
    "ANTHROPIC_BASE_URL": "https://api.example.com/v1"
  }
}
```

### Backups

**Location:** `~/.gate-cli/backups/`

- Automatic rotation (keeps last 10 backups)
- Named with timestamp: `settings.json.backup.YYYY-MM-DD-HH-mm-ss`
- Original backup preserved: `settings.json.original`

---

## Execution Modes

Gate-CLI supports two execution modes. See [Spring Shell Execution Documentation](https://docs.spring.io/spring-shell/reference/execution.html) for more details.

### Non-Interactive Mode (Single Command)

Execute a command directly and exit:

```bash
gate-cli config --client-id "my-client"
gate-cli login
gate-cli status
gate-cli logout
```

### Interactive Mode (Shell)

Start the interactive shell for multiple commands:

```bash
gate-cli
```

Then enter commands at the prompt:

```
gate-cli:>config --client-id "my-client"
gate-cli:>login
gate-cli:>status
gate-cli:>exit
```

---

## Security Considerations

### Local Tool Design

Gate-CLI is designed as a **local development tool**. Security relies on OS-level file permissions.

**What Gate-CLI Does:**
- ✅ Recommends file permissions (`chmod 600`)
- ✅ Atomic file operations to prevent corruption
- ✅ Filters sensitive information from logs
- ✅ Masks secrets in display output

**What Users Must Do:**
- 👤 Set appropriate file permissions
- 👤 Protect user account and computer
- 👤 Don't use in shared environments
- 👤 Rotate OAuth2 credentials regularly
- 👤 Don't commit config files to version control

**Credential Storage:**
- Stored in plaintext in `~/.gate-cli/config.json`
- Relies on OS file permissions for protection
- Simplified design for local CLI tool usage

**Not Suitable For:**
- ❌ Multi-user shared environments
- ❌ Production automation scripts
- ❌ Enterprise-grade security auditing
- ❌ Compliance requirements (PCI-DSS, etc.)

---

## Troubleshooting

### Missing Configuration

```
✗ Missing configuration: client-id
Use 'config --client-id <id>' to set it.
```

**Solution:** Set the required configuration using the `config` command.

### OAuth2 Authentication Failed

```
✗ OAuth2 authentication failed
Error: invalid_client
```

**Solutions:**
- Verify client ID and secret are correct
- Check if client is registered at OAuth2 server
- Ensure correct grant type is enabled (PKCE or Client Credentials)

### OIDC Discovery Failed

```
✗ Failed to discover OIDC configuration
```

**Solutions:**
- Verify issuer URI is correct
- Check that `/.well-known/openid-configuration` is accessible
- Test with: `curl https://auth.example.com/.well-known/openid-configuration`

### Callback URL Not Registered

If `login` fails with redirect error:

**Solution:** Register `http://localhost:8080/callback` as an allowed redirect URI in your OAuth2 provider.

### Permission Denied

```
✗ Configuration update failed
Error: Permission denied
```

**Solutions:**
- Check file permissions: `ls -la ~/.claude/settings.json`
- Fix permissions: `chmod 600 ~/.claude/settings.json`
- Ensure file ownership: `chown $USER ~/.claude/settings.json`

### Complete Reset

If you encounter unresolvable issues, you can completely reset Gate-CLI and Claude Code settings:

**Affected Files:**

| Path | Description |
|------|-------------|
| `~/.claude/settings.json` | Claude Code settings file (token and API URL written by Gate-CLI) |
| `~/.gate-cli/` | Gate-CLI configuration directory (contains config.json and backups) |

**Reset Script (macOS / Linux):**

```bash
#!/bin/bash
# Gate-CLI Complete Reset Script

echo "⚠️  This will delete all Gate-CLI configuration and Claude Code settings"
read -p "Are you sure you want to continue? (y/N) " confirm

if [[ "$confirm" =~ ^[Yy]$ ]]; then
    # Delete Claude Code settings
    if [ -f ~/.claude/settings.json ]; then
        rm ~/.claude/settings.json
        echo "✓ Deleted ~/.claude/settings.json"
    else
        echo "- ~/.claude/settings.json does not exist"
    fi

    # Delete Gate-CLI configuration directory
    if [ -d ~/.gate-cli ]; then
        rm -rf ~/.gate-cli
        echo "✓ Deleted ~/.gate-cli/"
    else
        echo "- ~/.gate-cli/ does not exist"
    fi

    echo ""
    echo "✓ Reset complete"
else
    echo "Cancelled"
fi
```

**Quick Reset (One-liner):**

```bash
# Delete all Gate-CLI related files
rm -f ~/.claude/settings.json && rm -rf ~/.gate-cli && echo "✓ Reset complete"
```

> **Note:** After reset, you need to run `gate-cli config` to set up configuration, then run `gate-cli login` or `gate-cli connect`.

---

## Development

### Build

```bash
# Build JAR
./gradlew clean build

# Skip tests
./gradlew clean build -x test

# Run tests
./gradlew test
```

### Run

```bash
# Run from JAR (interactive mode)
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar

# Run from JAR (non-interactive mode)
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar status

# Run with Gradle
./gradlew bootRun
```

### Native Compilation

```bash
# Build native image
./gradlew nativeCompile

# Run native executable
./build/native/nativeCompile/gate-cli
```

---

## Technology Stack

- **Spring Boot** 3.5.8
- **Spring Shell** 3.4.1 (using new `@Command` annotation model)
- **Java** 25
- **GraalVM Native** 0.10.6
- **Jackson** 2.x (JSON processing)
- **Lombok** (boilerplate reduction)

---

## License

This project is licensed under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Author

Sam Zhu ([@samzhu](https://github.com/samzhu))

## Acknowledgments

- Built with [Spring Shell](https://spring.io/projects/spring-shell)
- OAuth2 support powered by [Spring Security OAuth2 Client](https://spring.io/projects/spring-security-oauth)
- Native compilation with [GraalVM](https://www.graalvm.org/)
