# Daily Satori 测试指南

> 本文档说明如何运行和维护 Daily Satori 的编译验证流程，确保代码质量。

## 快速开始

### 日常开发验证

```bash
# 编译检查（推荐，每次修改后运行）
./gradlew :app:compileDebugKotlin

# 完整构建
./gradlew :app:assembleDebug

# 安装到设备
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug

# 启动 App
adb shell am start -n com.dailysatori/.MainActivity
```

## 环境配置

### 基础环境

确保已安装：
- JDK 21 (`/home/jimxl/.local/share/jdk-21.0.6`)
- Android SDK + Build Tools
- Gradle (通过 `./gradlew` 自动管理)

### AI 功能测试（可选）

在 App 设置中配置 AI 接口即可测试 AI 功能：
- API 地址（如 `https://api.deepseek.com`）
- API Token
- 模型名称（如 `deepseek-chat`）

## 测试设备

通过 `adb` 管理连接设备：

```bash
# 查看已连接设备
adb devices

# 安装 APK
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

## 推荐工作流程

### 开发阶段
```bash
./gradlew :app:compileDebugKotlin  # 每次修改后运行
```

### 功能完成
- 安装到设备手动测试
- 通过 logcat 查看运行时日志

### 发布前
```bash
# 完整构建
./gradlew :app:assembleDebug

# 安装测试
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug

# 查看日志
adb logcat -s "DBMigration:D" "MCPAgent:D" "MemoryExtract:D"
```

## 日志调试

### 关键 Tag

| Tag | 用途 |
|-----|------|
| `DBMigration` | 数据库迁移日志 |
| `MCPAgent` | AI Agent 处理日志 |
| `MemoryExtract` | 记忆提取日志 |
| `AiService` | AI API 调用日志 |

### 查看日志

```bash
# 实时查看
adb logcat -s TagName:*

# 查看崩溃
adb logcat -s AndroidRuntime:E

# 清除并重新查看
adb logcat -c && adb logcat -s TagName:*
```

## 故障排除

### 编译失败
1. 检查错误信息：`./gradlew :app:compileDebugKotlin 2>&1 | grep "e:"`
2. 清理重建：`./gradlew clean :app:assembleDebug`

### 安装失败
1. 检查设备连接：`adb devices`
2. 重启 adb：`adb kill-server && adb start-server`

### 运行时崩溃
1. 查看崩溃日志：`adb logcat -d -s AndroidRuntime:E`
2. 检查是否为数据库迁移问题（新增表未迁移）
3. 检查 Koin DI 注册是否完整

## 相关文档

- [编码规范](./01-coding-standards.md)
- [数据库迁移规则](../CLAUDE.md#数据库迁移规则)
