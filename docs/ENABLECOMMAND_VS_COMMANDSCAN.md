# @EnableCommand vs @CommandScan 比較

## 實驗結果總結

經過實際測試，以下是兩種註冊方式的詳細比較：

## 測試結果

| 註冊方式 | 需要類別級別 @Command | group 參數 | 優點 | 缺點 |
|---------|-------------------|-----------|------|------|
| `@EnableCommand` | ✅ **必須** | 可選（建議使用）| 明確控制、類型安全 | 新增命令類別需要更新列表 |
| `@CommandScan` | ✅ **必須** | 可選（建議使用）| 自動掃描、更方便 | 較少控制、可能掃描到不想要的類別 |

## 詳細測試過程

### 測試 1: @EnableCommand 不加類別級別 @Command

```java
// ❌ 失敗
@Component
public class ConfigurationCommands {
    @Command(command = "status")
    public String status() { }
}

@EnableCommand({ConfigurationCommands.class})
```

**結果：** 建置失敗
```
IllegalStateException: No Command annotation found on 'ConfigurationCommands'.
```

### 測試 2: @CommandScan 不加類別級別 @Command

```java
// ❌ 命令不會被註冊
@Component
public class ConfigurationCommands {
    @Command(command = "status")
    public String status() { }
}

@CommandScan
```

**結果：** 建置成功，但命令沒有出現在 help 列表中

### 測試 3: @CommandScan 加 @Command（不加 group）

```java
// ✅ 可以工作，但群組顯示為 "Default"
@Command
@Component
public class ConfigurationCommands {
    @Command(command = "status")
    public String status() { }
}

@CommandScan
```

**結果：**
```
Default
       restore: ...
       status: ...
```

### 測試 4: @CommandScan 加 @Command(group = "...")

```java
// ✅ 完美！
@Command(group = "Configuration Commands")
@Component
public class ConfigurationCommands {
    @Command(command = "status")
    public String status() { }
}

@CommandScan
```

**結果：**
```
Configuration Commands
       restore: ...
       status: ...
```

## 結論與建議

### 使用 @CommandScan 的優點

1. **更方便**：不需要在 Application 類別中明確列出每個命令類別
2. **自動發現**：新增命令類別時，只要加上 `@Command` 和 `@Component` 就會自動被掃描到
3. **符合官方建議**：Spring Shell 文件建議在 Spring Boot App 類別使用 `@CommandScan`

### 使用 @EnableCommand 的優點

1. **明確控制**：清楚知道哪些類別被註冊為命令
2. **類型安全**：IDE 可以檢查類別是否存在
3. **避免意外**：不會掃描到不想要的命令類別

### 本專案建議

對於 gate-cli 專案，**建議使用 `@CommandScan`**，原因：

1. 命令類別數量少（目前只有 2 個）
2. 不太可能有不想要的命令類別被掃描到
3. 未來新增命令類別更方便
4. 符合 Spring Shell 最佳實踐

### 必須遵守的規則

無論使用哪種方式，都必須：

1. ✅ 類別必須有 `@Command` 註解
2. ✅ 類別必須有 `@Component` 註解
3. ✅ 建議使用 `group` 參數組織命令（否則會顯示在 "Default" 群組）
4. ✅ 方法必須有 `@Command(command = "...")` 註解

## 已更新

已將本專案從 `@EnableCommand` 切換到 `@CommandScan`：

```java
// Before
@EnableCommand({ConnectionCommands.class, ConfigurationCommands.class})

// After
@CommandScan
```

所有命令正常工作，測試通過！
