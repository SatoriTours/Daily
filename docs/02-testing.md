# Daily Satori 测试指南

> 本文档说明如何运行和维护 Daily Satori 的编译验证流程，确保代码质量。

## 快速开始

### 日常开发验证

日常仅执行代码级测试（如单元测试）及必要的编译检查，不启动模拟器、不安装或启动 App，也不执行 UI 测试。
按改动范围选择聚焦测试；代码改动完成后运行相关测试和必要的编译检查，纯文档修改无需构建。验证频率和完整构建的触发条件统一遵守 `AGENTS.md` 的“代码校验”；同一代码和环境已有有效结果时不重复运行。

```bash
# 代码改动完成后的编译检查
./gradlew :app:compileDebugKotlin

# 两个模块的单元测试（聚焦改动可用 --tests 限定测试类）
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest
```

## 环境配置

### 基础环境

确保已安装：
- JDK 21（Arch Linux：`/usr/lib/jvm/java-21-openjdk`）
- Android SDK Platform 36、Build Tools 35.0.0（当前 AGP 默认版本）
- Gradle 8.14（通过 `./gradlew` 自动管理）

Arch Linux 可运行 `bash scripts/init-dev-env.sh --skip-gradle-check` 安装基础环境，
然后重新打开 Bash 终端。脚本默认安装 SDK 到 `~/Android/Sdk`，并配置 `~/.bashrc`。
脚本安装的 Build Tools 36.0.0 可与 Gradle 自动安装的 35.0.0 共存。

需要完整构建且改动涉及两个模块时，可一次执行编译、APK 构建和两个模块的单元测试：

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug \
  :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache
```

测试报告位于 `app/build/reports/tests/testDebugUnitTest/` 和
`shared/build/reports/tests/testDebugUnitTest/`。当前未配置 Android instrumentation
测试；模拟器上的安装、启动和界面检查需另外执行。

### Android 模拟器

仅发布代码前，或用户明确要求真机／UI 测试时，才启动模拟器进行 UI 验证。
用户说“真机测试”时默认使用此模拟器；明确指定物理设备时使用指定设备。
UI 测试完成、失败或中断后均须执行 `adb -e emu kill` 关闭模拟器，不留后台运行。

安装并创建 API 36 设备（首次执行；创建时不要覆盖已有同名设备）：

```bash
sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"
printf 'no\n' | avdmanager create avd -n DailySatori_API_36 \
  -k "system-images;android-36;google_apis;x86_64" -d pixel_2
avdmanager list avd
```

本机 `avdmanager` 将设备放在 `~/.config/.android/avd`，已在 `~/.bashrc`
设置 `ANDROID_AVD_HOME` 指向该目录，并将 `$ANDROID_HOME/emulator` 加入 `PATH`。
其他机器应以 `avdmanager list avd` 的实际路径为准。

```bash
emulator -accel-check
# 有 KVM 和桌面的机器可直接启动窗口
emulator -avd DailySatori_API_36
# 有 KVM 的无桌面容器
emulator -avd DailySatori_API_36 -no-window -no-audio -no-boot-anim \
  -no-snapshot -gpu swiftshader -accel on -cores 4 -memory 2048
# 无 /dev/kvm 的容器使用软件模拟；首次启动可能耗时较长
emulator -avd DailySatori_API_36 -no-window -no-audio -no-boot-anim \
 -no-snapshot -gpu swiftshader -accel off -cores 2 -memory 2048
```

当前容器已开放 `/dev/kvm`，`emulator -accel-check` 确认 KVM 可用。
硬件加速实测开机约 17 秒，App 冷启动约 3.7 秒，`am start -W` 返回
`Status: ok`。此前无 KVM 时，软件模拟开机约 11 分钟且 App 出现 ANR；
UI 运行验证应使用 KVM 或真机。

另开终端，等待 `adb -e shell getprop sys.boot_completed` 返回 `1` 后：

```bash
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
adb -e shell am start -W -n com.dailysatori/.MainActivity
adb -e logcat -d -b crash
```

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
./gradlew :app:installDebug
```

## 推荐工作流程

### 代码改动完成
```bash
./gradlew :app:compileDebugKotlin
```

同时按改动范围运行相关模块的单元测试，不启动模拟器；不重复执行上述已通过的编译检查。

### 发布前或用户明确要求真机／UI 测试时

启动模拟器，安装 App 并检查界面及交互；测试完成、失败或中断后都关闭模拟器。

```bash
# 完整构建
./gradlew :app:assembleDebug

# 安装测试
./gradlew :app:installDebug

# 查看日志
adb logcat -s "DBMigration:D" "MCPAgent:D" "MemoryExtract:D"

# UI 测试结束后（先退出日志查看）
adb -e emu kill
```

## 本地诊断导出验证

入口：设置 → 诊断与日志。默认导出点击时刻之前 30 分钟的安全事件；崩溃现场单独导出。

- 代码级聚焦测试：`:app:testDebugUnitTest --tests 'com.dailysatori.core.diagnostics.*'`、`:shared:testDebugUnitTest --tests 'com.dailysatori.service.diagnostics.*'`。
- 覆盖安全投影、业务失败、真实本地 HTTP/重定向/断流/取消、Ktor trace、滚动容量、时间窗口、损坏尾行、磁盘错误、崩溃隔夜恢复、导出取消和流关闭失败。
- Ktor 引擎包装使用当前 3.1.3 的 InternalAPI 来传递请求属性；升级 Ktor 时必须重跑 trace 和流式测试。
- 仅发布前或用户明确要求时验证 Android 保存选择器、跨配置变化、系统退出信息与实际强杀。日志末尾及严重 OOM 不保证完整。
- 旧任务中心历史日志不会自动脱敏或并入报告；新 HTTP 任务日志只写安全摘要。Kermit 的未知自由文本不再输出到 Logcat，改为安全诊断事件。

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
- [数据库迁移规则](../AGENTS.md#数据库迁移规则)
