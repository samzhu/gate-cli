# Gate-CLI

**Simplify Claude Code configuration with OAuth2-protected custom API endpoints**

Gate-CLI is a command-line tool that automates the configuration of Claude Code to use custom API endpoints protected by OAuth2 authentication.

## Features

- **Automatic OAuth2 Authentication** - Handles OAuth2 Client Credentials flow automatically
- **Configuration Management** - Safely updates and restores Claude Code settings
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

### Option 2: Native Compilation (Coming Soon)

```bash
# Build native image
./gradlew nativeCompile

# Run native executable
./build/native/nativeCompile/gate-cli
```

## Quick Start

### 1. Check Current Status

```bash
gate-cli:>status
```

### 2. Connect to OAuth2 Protected API

```bash
gate-cli:>connect \
  --client-id "your-client-id" \
  --client-secret "your-client-secret" \
  --token-url "https://oauth.example.com/token" \
  --api-url "https://api.example.com/v1"
```

Output:
```
� Connecting to OAuth2 server...
 Connected to OAuth2 server
 Obtained access token
 Updated Claude Code settings
 Saved connection configuration

Configuration Summary:
  API URL: https://api.example.com/v1
  Token expires: 2025-11-24 11:30:45
  Settings file: ~/.claude/settings.json
```

### 3. Refresh Token

```bash
gate-cli:>refresh
```

### 4. Check Status Again

```bash
gate-cli:>status
```

Output:
```
Gate-CLI Status
===============

Status: Connected
API URL: https://api.example.com/v1
Token Status: Valid (expires in 55 minutes)
Last Connected: 2025-11-24 10:30:45
Available Backups: 3

Claude Code Settings:
  Location: ~/.claude/settings.json
  Last Modified: 2025-11-24 10:30:46
```

### 5. Disconnect (Restore Original Settings)

```bash
gate-cli:>disconnect
```

## Commands Reference

### Connection Management

#### `connect`
Connect to OAuth2 server and configure Claude Code.

**Options:**
- `-i, --client-id` (required) - OAuth2 client ID
- `-s, --client-secret` (required) - OAuth2 client secret
- `-t, --token-url` (required) - OAuth2 token endpoint URL
- `-a, --api-url` (required) - Custom Claude API endpoint URL
- `-f, --force` (optional) - Force overwrite without prompt

**Example:**
```bash
connect -i "client-id" -s "secret" -t "https://oauth.example.com/token" -a "https://api.example.com/v1"
```

#### `disconnect`
Disconnect and restore original Claude Code settings.

**Example:**
```bash
disconnect
```

#### `refresh`
Refresh access token using stored credentials.

**Note:** Only available when connected.

**Example:**
```bash
refresh
```

### Configuration Management

#### `status`
Show current connection status and configuration.

**Example:**
```bash
status
```

#### `restore`
Restore Claude Code settings from a backup.

**Options:**
- `-b, --backup` (optional) - Specific backup file to restore
- `-l, --list` (optional) - List available backups

**Examples:**
```bash
# Restore most recent backup
restore

# List all backups
restore --list

# Restore specific backup
restore --backup ~/.gate-cli/backups/settings.json.backup.2025-11-24-10-00-00
```

## Configuration Files

### Gate-CLI Configuration
**Location:** `~/.gate-cli/config.json`

Stores connection information and credentials (plaintext).

```json
{
  "version": "1.0",
  "currentConnection": {
    "clientId": "client-id",
    "clientSecret": "client-secret",
    "tokenUrl": "https://oauth.example.com/token",
    "apiUrl": "https://api.example.com/v1",
    "lastConnected": "2025-11-24T10:30:45Z",
    "tokenExpiration": "2025-11-24T11:30:45Z"
  },
  "backupSettings": {
    "maxBackups": 10,
    "backupDirectory": "~/.gate-cli/backups"
  }
}
```

### Claude Code Settings
**Location:** `~/.claude/settings.json`

Modified by Gate-CLI to include custom endpoint and bearer token.

### Backups
**Location:** `~/.gate-cli/backups/`

- Automatic rotation (keeps last 10 backups)
- Named with timestamp: `settings.json.backup.YYYY-MM-DD-HH-mm-ss`
- Original backup preserved: `settings.json.original`

## Architecture

```
Commands (User Interaction)
    �
Services (Business Logic)
    �
Models (Data Structures) + Utils (File Operations)
```

### Key Components

- **OAuth2Service** - Handles OAuth2 Client Credentials flow
- **ClaudeConfigService** - Manages `~/.claude/settings.json`
- **BackupService** - Creates and rotates backups
- **ConfigurationService** - Manages `~/.gate-cli/config.json`
- **FileUtil** - Atomic file operations (temp + rename pattern)

## Security Considerations

### Local Tool Design
Gate-CLI is designed as a **local development tool**. Security relies on OS-level file permissions.

**What Gate-CLI Does:**
-  Recommends file permissions (`chmod 600`)
-  Atomic file operations to prevent corruption
-  Filters sensitive information from logs
-  Warns about HTTP (non-HTTPS) connections

**What Users Must Do:**
- =d Set appropriate file permissions
- =d Protect user account and computer
- =d Don't use in shared environments
- =d Rotate OAuth2 credentials regularly
- =d Don't commit config files to version control

**Credential Storage:**
- Stored in plaintext in `~/.gate-cli/config.json`
- Relies on OS file permissions for protection
- Simplified design for local CLI tool usage

**Not Suitable For:**
- L Multi-user shared environments
- L Production automation scripts
- L Enterprise-grade security auditing
- L Compliance requirements (PCI-DSS, etc.)

## Troubleshooting

### OAuth2 Authentication Failed
```
 OAuth2 authentication failed
Error: invalid_client
```

**Solutions:**
- Verify client ID and secret are correct
- Check if client is registered at OAuth2 server
- Ensure `client_credentials` grant type is enabled

### Connection Refused
```
 Connection failed
Error: Unable to connect to token URL
```

**Solutions:**
- Verify token URL is correct and accessible
- Check network connection
- Verify firewall/proxy settings
- Test with: `curl -I https://oauth.example.com/token`

### Permission Denied
```
 Configuration update failed
Error: Permission denied
```

**Solutions:**
- Check file permissions: `ls -la ~/.claude/settings.json`
- Fix permissions: `chmod 600 ~/.claude/settings.json`
- Ensure file ownership: `chown $USER ~/.claude/settings.json`

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
# Run from JAR
java -jar build/libs/gate-cli-0.0.1-SNAPSHOT.jar

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

## Technology Stack

- **Spring Boot** 3.5.8
- **Spring Shell** 3.4.1 (using new `@Command` annotation model)
- **Java** 25
- **GraalVM Native** 0.10.6
- **Jackson** 2.x (JSON processing)
- **Lombok** (boilerplate reduction)

### Architecture Notes

This project uses **Spring Shell 3.x new `@Command` annotation model** (introduced in 3.1.x):
- ✅ Class-level `@Command(group = "...")` for command grouping
- ✅ Method-level `@Command(command = "...")` for individual commands
- ✅ `@CommandAvailability` for dynamic command availability
- ✅ Future-proof (legacy `@ShellComponent` will be deprecated)

**📚 Documentation:**
- [Quick Reference](SPRING_SHELL_QUICK_REF.md) - Fast lookup for correct usage
- [Detailed Guide](docs/SPRING_SHELL_COMMAND_GUIDE.md) - Complete guide with examples

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
