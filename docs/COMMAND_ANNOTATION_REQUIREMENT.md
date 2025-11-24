# Spring Shell @Command 註解需求說明

## 問題

你問：「官方文件哪裡有說 @Command(group = "...") // ← 必須！定義命令組?」

## 答案

你的質疑是正確的！讓我澄清：

### 官方文件說什麼？

根據 [Spring Shell 官方文件](https://docs.spring.io/spring-shell/reference/commands/registration/annotation.html)：

> 類別級別的 `@Command` 註解是**可選的**，用於「定義預設值或共享設定」。

官方範例顯示可以這樣做：
```java
class Example {
    @Command(command = "example")
    public String example() {
        return "Hello";
    }
}
```

**沒有類別級別的 `@Command` 也能工作！**

### 但為什麼我們需要它？

關鍵在於**註冊方式**。

#### 使用 @EnableCommand 時

我們的專案使用 `@EnableCommand` 來註冊命令類別：

```java
@EnableCommand({ConnectionCommands.class, ConfigurationCommands.class})
public class GateCliApplication { }
```

當使用 `@EnableCommand` 時，Spring Shell 的實作（`CommandRegistrationBeanRegistrar`）會：
1. 檢查每個指定的類別
2. **要求類別必須有 `@Command` 註解**
3. 如果沒有，拋出 `IllegalStateException`

#### 實驗驗證

我們測試了移除 `@Command(group = "Configuration Commands")`：

```java
// ❌ 移除類別級別 @Command
@Component
public class ConfigurationCommands {
    @Command(command = "status")
    public String status() { }
}
```

**結果：**
```
Exception in thread "main" java.lang.IllegalStateException:
No Command annotation found on 'io.github.samzhu.gate.command.ConfigurationCommands'.
	at org.springframework.shell.command.annotation.support.CommandRegistrationBeanRegistrar
```

### 總結

| 註冊方式 | 類別級別 @Command | 是否必須 |
|---------|-----------------|---------|
| `@EnableCommand` | `@Command(...)` | ✅ **必須** |
| `@CommandScan` | `@Command(...)` | ❓ 可能不需要（未測試）|
| 一般情況（官方文件）| `@Command(...)` | ❌ 可選 |

### 關於 `group` 參數

`@Command` 註解有多個參數：

- **`command`**: 定義命令名稱（可創建階層式命令，如 "parent child"）
- **`group`**: 定義命令群組（用於在 help 輸出中組織命令）
- **`description`**: 命令說明
- **`alias`**, **`hidden`**, **`interactionMode`**: 其他功能

我們使用 `group` 來組織命令，所以在 help 中會看到：
```
Configuration Commands
       restore: ...
       status: ...

Connection Commands
       connect: ...
       disconnect: ...
```

## 參考資料

- [Spring Shell 官方文件 - Command Registration](https://docs.spring.io/spring-shell/reference/commands/registration/annotation.html)
- [Spring Shell API - @Command](https://docs.spring.io/spring-shell/docs/current/api/org/springframework/shell/command/annotation/Command.html)
- Spring Shell 原始碼：`CommandRegistrationBeanRegistrar.java`

## 結論

1. **官方文件的通用說法**：類別級別 `@Command` 是可選的
2. **實際使用 `@EnableCommand` 時**：類別級別 `@Command` 是必須的
3. **我們的文件**：已更新，明確說明這是 `@EnableCommand` 的特定需求

感謝你的質疑！這讓文件更加準確和清晰。
