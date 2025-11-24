# Spring Shell 3.x @Command 註解使用指南

## ⚠️ 重要：正確使用方式

本專案使用 **Spring Shell 3.1.x+ 新的 `@Command` 註解模型**，而非舊的 `@ShellComponent` + `@ShellMethod`（後者將被棄用）。

---

## ✅ 正確的實作方式

### 1. 類別層級 @Command（使用 @EnableCommand 時必須）

```java
@Command(group = "Connection Commands")  // ← 使用 @EnableCommand 時必須！定義命令組
@Component                               // ← Spring Bean
@RequiredArgsConstructor
public class ConnectionCommands {
    // ...
}
```

**重要說明：**
- 根據 [Spring Shell 官方文件](https://docs.spring.io/spring-shell/reference/commands/registration/annotation.html)，類別級別的 `@Command` 一般來說是可選的
- **但是**，當使用 `@EnableCommand` 註冊命令類別時，Spring Shell 的 `CommandRegistrationBeanRegistrar` 會檢查類別是否有 `@Command` 註解
- 如果缺少，會拋出 `IllegalStateException: No Command annotation found on 'YourClass'`

**❌ 錯誤：** 使用 `@EnableCommand` 但缺少類別級別的 `@Command`
```java
@Component  // ❌ 只有 @Component 會導致 AOT 編譯失敗
public class ConnectionCommands {
    @Command(command = "connect")
    public String connect() { }
}

// 在 Application 類別中
@EnableCommand({ConnectionCommands.class})  // 會失敗！
```

**錯誤訊息：**
```
Exception in thread "main" java.lang.IllegalStateException:
No Command annotation found on 'io.github.samzhu.gate.command.ConnectionCommands'.
```

---

### 2. 方法層級定義具體命令

```java
@Command(command = "connect", description = "Connect to OAuth2 server and configure Claude Code")
public String connect(
    @Option(required = true, longNames = "client-id", shortNames = 'i',
            description = "OAuth2 client ID") String clientId,
    @Option(required = true, longNames = "client-secret", shortNames = 's',
            description = "OAuth2 client secret") String clientSecret
) {
    // 實作
}
```

**關鍵點：**
- `command = "connect"` - 定義命令名稱
- `description = "..."` - 命令說明（顯示在 help 中）
- `@Option` - 命令參數
  - `required` - 是否必填
  - `longNames` - 長選項名（如 `--client-id`）
  - `shortNames` - 短選項名（如 `-i`）
  - `description` - 參數說明

---

### 3. 命令可用性控制

```java
@Command(command = "refresh", description = "Refresh access token using stored credentials")
@CommandAvailability(provider = "connectedAvailability")
public String refresh() {
    // 只有在連線狀態下才可用
}
```

**對應的 AvailabilityProvider：**
```java
@Component
public class ConnectedAvailability implements AvailabilityProvider {

    @Override
    public Availability get() {
        if (configurationService.isConnected()) {
            return Availability.available();
        }
        return Availability.unavailable("you are not connected. Use 'connect' command first.");
    }
}
```

**注意：**
- Bean name 預設為類別名首字母小寫：`ConnectedAvailability` → `"connectedAvailability"`
- 在 `@CommandAvailability(provider = "...")` 中使用這個 bean name

---

### 4. 啟用命令掃描

在主應用程式類別中：

**選項 A：使用 @CommandScan（推薦，自動掃描）**
```java
@SpringBootApplication
@CommandScan  // 自動掃描所有 @Command 註解的類別
public class GateCliApplication {
    // ...
}
```

**優點：**
- 自動發現命令類別，不需要明確列出
- 新增命令類別時更方便
- 符合 Spring Shell 官方建議

**選項 B：使用 @EnableCommand（明確控制）**
```java
@SpringBootApplication
@EnableCommand({ConnectionCommands.class, ConfigurationCommands.class})
public class GateCliApplication {
    // ...
}
```

**優點：**
- 明確控制哪些類別被註冊
- IDE 類型安全檢查

**⚠️ 重要：** 無論使用哪種方式，命令類別都**必須**有類別級別的 `@Command` 註解。

---

## 📋 完整範例

### ConnectionCommands.java
```java
package io.github.samzhu.gate.command;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

@Command(group = "Connection Commands")  // ← 類別級別
@Component
@RequiredArgsConstructor
public class ConnectionCommands {

    private final OAuth2Service oauth2Service;
    private final ConnectedAvailability connectedAvailability;

    @Command(command = "connect", description = "Connect to OAuth2 server")  // ← 方法級別
    public String connect(
            @Option(required = true, longNames = "client-id", shortNames = 'i') String clientId,
            @Option(required = true, longNames = "client-secret", shortNames = 's') String clientSecret
    ) {
        // 實作
        return "Connected successfully!";
    }

    @Command(command = "disconnect", description = "Disconnect from server")
    public String disconnect() {
        // 實作
        return "Disconnected!";
    }

    @Command(command = "refresh", description = "Refresh access token")
    @CommandAvailability(provider = "connectedAvailability")  // ← 可用性控制
    public String refresh() {
        // 實作
        return "Token refreshed!";
    }
}
```

### GateCliApplication.java
```java
package io.github.samzhu.gate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@CommandScan  // ← 自動掃描所有命令類別（推薦）
public class GateCliApplication {

    public static void main(String[] args) {
        SpringApplication.run(GateCliApplication.class, args);
    }
}
```

---

## 🚫 常見錯誤

### 錯誤 1：缺少類別級別 @Command

```java
// ❌ 錯誤：只有方法級別 @Command，沒有類別級別
@Component
public class MyCommands {
    @Command(command = "test")
    public String test() { }
}

// Application 類別
@CommandScan  // 或 @EnableCommand({MyCommands.class})
public class App { }
```

**使用 @EnableCommand 時的錯誤訊息：**
```
Exception in thread "main" java.lang.IllegalStateException:
No Command annotation found on 'MyCommands'.
```

**使用 @CommandScan 時的結果：**
- 建置成功，但命令不會出現在 help 列表中

**原因：**
- 雖然官方文件說類別級別的 `@Command` 是可選的
- 但在實際使用 `@EnableCommand` 或 `@CommandScan` 時，類別級別的 `@Command` 都是必須的

**解決方案：**
```java
// ✅ 正確
@Command(group = "My Commands")  // ← 必須加上類別級別 @Command
@Component
public class MyCommands { }
```

---

### 錯誤 2：混用 Legacy 和新 API

```java
// ❌ 錯誤：混用兩種 API
@ShellComponent  // Legacy
public class MyCommands {
    @Command(command = "test")  // 新 API
    public String test() { }
}
```

**解決方案：** 統一使用新的 `@Command` API

---

### 錯誤 3：忘記啟用命令掃描

```java
// ❌ 錯誤：沒有 @EnableCommand 或 @CommandScan
@SpringBootApplication
public class MyApp { }
```

**結果：** 命令不會被註冊

**解決方案：** 加上 `@EnableCommand` 或 `@CommandScan`

---

## 📚 參考資料

- [Spring Shell 官方文檔](https://docs.spring.io/spring-shell/reference/index.html)
- [Command Registration](https://docs.spring.io/spring-shell/reference/commands/registration/annotation.html)
- [Command Availability](https://docs.spring.io/spring-shell/reference/commands/availability.html)

---

## 🔄 從 Legacy API 遷移

如果你有舊的 `@ShellComponent` 程式碼：

### Before (Legacy):
```java
@ShellComponent
public class MyCommands {
    @ShellMethod(value = "Test command", key = "test")
    public String test(
        @ShellOption(value = {"-n", "--name"}) String name
    ) { }
}
```

### After (新 API):
```java
@Command(group = "My Commands")
@Component
public class MyCommands {
    @Command(command = "test", description = "Test command")
    public String test(
        @Option(longNames = "name", shortNames = 'n') String name
    ) { }
}
```

**主要變更：**
1. `@ShellComponent` → `@Command(group = "...")` + `@Component`
2. `@ShellMethod(key = "...")` → `@Command(command = "...")`
3. `@ShellOption(value = {...})` → `@Option(longNames = "...", shortNames = '...')`
4. 需要在主類別加 `@EnableCommand` 或 `@CommandScan`

---

## ✅ 檢查清單

在實作新命令時，確保：

- [ ] 類別有 `@Command(group = "...")` 註解
- [ ] 類別有 `@Component` 註解
- [ ] 方法有 `@Command(command = "...")` 註解
- [ ] 參數使用 `@Option` 註解
- [ ] 如果需要動態可用性，實作 `AvailabilityProvider` 並使用 `@CommandAvailability`
- [ ] 主應用程式類別有 `@EnableCommand` 或 `@CommandScan`
- [ ] 測試命令是否正確顯示在 `help` 中

---

**最後更新：** 2025-11-24
**Spring Shell 版本：** 3.4.1
**專案：** gate-cli
