# Daily Satori 测试使用说明

## 概述

项目已经重构了测试脚本，现在使用统一的 `run_tests.sh` 脚本来运行所有测试，支持本地 Android 模拟器。

## 使用方法

### 快速测试（推荐）
```bash
./run_tests.sh --quick
# 或
./run_tests.sh -q
```

运行核心功能测试，包括：
- 应用启动和基本渲染测试
- 列表显示和滚动测试
- 输入和按钮功能测试
- 基础UI组件测试

### 完整测试
```bash
./run_tests.sh
```

运行所有可用的测试用例。

### 调试环境
```bash
./test_debug.sh
```

检查测试环境，包括Flutter环境、设备状态、依赖安装等。

## 脚本功能

### 自动检测和管理模拟器
- 自动检测可用的Android模拟器
- 如果没有运行中的模拟器，会自动启动默认模拟器
- 自动获取设备ID

### 智能测试选择
- 只运行存在的测试文件
- 跳过不存在或失败的测试文件
- 提供清晰的测试结果反馈

### 超时保护
- 为每个测试设置合理的超时时间
- 防止测试无限期挂起

## 修改记录

1. **合并脚本**：删除了重复的 `quick_test.sh`，统一使用 `run_tests.sh`
2. **模拟器支持**：修复了设备ID获取逻辑，支持本地Android模拟器
3. **超时修复**：修正了Flutter测试的超时参数格式
4. **权限增强**：为Android应用添加了必要的权限
5. **错误处理**：改进了错误处理和用户反馈

## 故障排除

### 模拟器相关问题
- 确保已创建Android模拟器：`flutter emulators`
- 手动启动模拟器：`flutter emulators --launch <emulator_id>`
- 检查设备连接：`flutter devices`

### 测试失败
- 运行调试脚本检查环境：`./test_debug.sh`
- 清理项目重新构建：`flutter clean && flutter pub get`
- 单独运行特定测试：`flutter test integration_test/quick_test.dart -d <device_id>`

### 权限问题
```bash
chmod +x run_tests.sh test_debug.sh
```