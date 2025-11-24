# Spring Shell 3.x @Command 快速參考

## ⚡ 快速模板

### 1. 命令類別 (必須兩個註解)
```java
@Command(group = "My Commands")  // ← 必須！定義命令群組
@Component                       // ← 必須！Spring Bean
public class MyCommands { }
```

**重要：**
- 無論使用 `@EnableCommand` 或 `@CommandScan`，類別級別的 `@Command` 都是必須的
- `group` 參數可選，但建議使用（否則會顯示在 "Default" 群組）

### 2. 命令方法
```java
@Command(command = "my-cmd", description = "...")
public String myCommand(
    @Option(required = true, longNames = "name", shortNames = 'n') String name
) {
    return "Result";
}
```

### 3. 啟用掃描 (主應用程式)
```java
@SpringBootApplication
@CommandScan  // 推薦：自動掃描所有命令
// 或
@EnableCommand({MyCommands.class})  // 明確指定
public class App { }
```

---

## 🔴 最常見錯誤

### 錯誤：缺少類別級別 @Command
```java
// ❌ 錯誤 - 會導致 "No Command annotation found" 錯誤
@Component
public class MyCommands { }

// ✅ 正確
@Command(group = "...")
@Component
public class MyCommands { }
```

---

## 📋 完整檢查清單

- [ ] 類別：`@Command(group = "...")`
- [ ] 類別：`@Component`
- [ ] 方法：`@Command(command = "...")`
- [ ] 主類：`@EnableCommand(...)` 或 `@CommandScan`

---

詳細說明見：`docs/SPRING_SHELL_COMMAND_GUIDE.md`
