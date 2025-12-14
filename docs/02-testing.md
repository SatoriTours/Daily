# 🧪 Daily Satori 测试指南

> 本文档说明如何运行和维护 Daily Satori 的测试套件，确保代码质量和应用稳定性。

## 🎯 快速开始

### 统一测试脚本

```bash
# 每日开发 - 快速测试（推荐）
./test.sh

# 指定测试类型
./test.sh quick         # 快速测试（默认）
./test.sh basic         # 基础集成测试
./test.sh full          # 完整功能测试
./test.sh comprehensive # 综合功能测试

# 环境检查
./test.sh --check       # 检查测试环境
./test.sh --prepare     # 准备测试环境
./test.sh --help        # 查看帮助
```

## 📋 测试类型

### 1. 快速测试 (Quick Test)

**用途**：日常开发中快速验证代码质量
**包含内容**：
- 代码静态分析 (`flutter analyze`)
- 单元测试 (`flutter test`)
- 应用构建检查 (`flutter build apk --debug`)

### 2. 基础集成测试 (Basic Integration Test)

**用途**：验证应用能够正常启动并显示主界面
**包含内容**：
- 快速测试的所有内容
- 应用启动测试
- 主界面显示验证

### 3. 完整功能测试 (Full Feature Test)

**用途**：测试核心业务流程
**包含内容**：
- 基础集成测试的所有内容
- 文章保存功能
- 日记创建功能
- 书籍添加功能

### 4. 综合功能测试 (Comprehensive Test)

**用途**：全面测试所有功能模块
**包含内容**：
- 完整功能测试的所有内容
- AI 聊天功能
- 设置功能（主题、语言）
- 备份还原功能
- 文章详情页功能
- 读书感悟功能

## 🔧 环境配置

### 基础环境

确保已安装：
- Flutter SDK
- Android Studio / Xcode
- 测试设备或模拟器

### AI 功能测试（可选）

如需测试 AI 功能，请配置环境变量：

```bash
# Linux/macOS
export TEST_AI_URL="https://api.deepseek.com"
export TEST_AI_TOKEN="sk-your-openai-api-key-here"
export TEST_AI_MODEL="deepseek-chat"

# Windows PowerShell
$env:TEST_AI_URL="https://api.deepseek.com"
$env:TEST_AI_TOKEN="sk-your-openai-api-key-here"
$env:TEST_AI_MODEL="deepseek-chat"
```

**注意**：
- 未配置时，AI 功能测试会被跳过
- API Key 不会输出到日志中
- 可以使用任何兼容 OpenAI API 格式的服务

## 📱 测试设备

脚本会自动检测连接的设备。如果没有检测到设备：

1. 确保设备已连接
2. 运行 `flutter devices` 查看可用设备
3. 如果有多个设备，脚本默认使用 `PKJ110`

## 🚀 推荐工作流程

### 开发阶段
```bash
./test.sh  # 每次修改后运行
```

### 功能完成
- 手动测试相关功能
- 运行基础集成测试验证稳定性

### 发布前
```bash
./test.sh comprehensive  # 完整测试（包含所有检查）
```

## 📊 测试覆盖的功能

### ✅ 自动化测试

1. **应用启动测试**
   - 应用正常启动（15秒等待）
   - 底部导航栏显示
   - 导航项（文章、日记、读书）正常

2. **基础组件测试**
   - UI组件渲染
   - 应用响应性
   - 内存稳定性

3. **代码质量检查**
   - `flutter analyze` 静态分析
   - 单元测试

### 🔄 完整功能测试步骤

1. **文章功能测试**
   - 自动保存文章到剪贴板
   - 从剪贴板导入文章
   - 测试文章刷新功能
   - 测试文章删除功能

2. **日记功能测试**
   - 自动创建测试日记
   - 从相册添加图片
   - 保存日记

3. **书籍功能测试**
   - 搜索并添加"论语"
   - 验证书籍信息

## 📊 测试报告

### 成功输出示例

```
[INFO] 准备测试环境...
[SUCCESS] 环境准备完成
[INFO] 开始快速测试...
[INFO] 运行代码静态分析...
Analyzing Daily...
No issues found! (ran in 1.5s)
[SUCCESS] ✓ 代码分析通过
[INFO] 运行单元测试...
00:00 +0: loading...
00:02 +3: All tests passed!
[SUCCESS] ✓ 单元测试通过
[INFO] 检查构建...
✓ Built build/app/outputs/flutter-apk/app-debug.apk
[SUCCESS] ✓ 构建成功
[SUCCESS] 🎉 快速测试完成！代码可以提交了。
```

### 常见错误

1. **设备未连接**
   ```
   [ERROR] 需要连接设备才能运行集成测试
   ```
   解决：连接设备或启动模拟器

2. **代码分析失败**
   ```
   [ERROR] ✗ 代码分析失败
   ```
   解决：运行 `flutter analyze` 查看具体问题

3. **构建失败**
   ```
   [ERROR] ✗ 构建失败
   ```
   解决：检查依赖版本，运行 `flutter pub get`

## ⚠️ 注意事项

### 应用启动问题
- **现象**: 第一次启动可能卡在启动页
- **解决**: 重启一次即可恢复正常
- **测试**: 集成测试使用15秒等待时间处理此问题

### 测试限制
- 由于错误处理器冲突，完整UI交互测试暂时无法自动化
- 核心功能（文章、日记、AI等）需要手动测试验证

## 🛠️ 维护测试

### 添加新的测试用例

1. **单元测试**
   - 在 `test/` 目录添加测试文件
   - 遵循 `*_test.dart` 命名规范

2. **集成测试**
   - 在 `integration_test/` 目录添加测试文件
   - 使用 `TestConfig` 管理测试数据

### 更新测试配置

编辑 `integration_test/test_config.dart`：
- 添加新的测试 URL
- 更新测试模板
- 修改等待时间

### 调试测试

1. **查看详细日志**
   ```bash
   flutter test --verbose
   ```

2. **运行特定测试**
   ```bash
   flutter test test/widget_test.dart
   ```

3. **调试集成测试**
   ```bash
   flutter test integration_test/full_app_test.dart --debug
   ```

## 📝 最佳实践

### 1. 测试命名

- 描述性命名：清楚说明测试内容
- 使用 Given-When-Then 模式
- 中文注释提高可读性

### 2. 测试结构

```dart
group('功能模块名', () {
  setUp(() {
    // 准备测试环境
  });

  testWidgets('具体测试场景', (tester) async {
    // Given - 准备数据
    // When - 执行操作
    // Then - 验证结果
  });

  tearDown(() {
    // 清理测试环境
  });
});
```

### 3. 等待策略

- 使用 `pumpAndSettle()` 等待异步操作
- 设置合理的超时时间
- 避免硬编码等待时间

### 4. 错误处理

- 使用 try-catch 捕获异常
- 提供清晰的错误信息
- 不让单个测试失败影响整体

## 🔍 测试覆盖范围

| 模块 | 测试内容 | 测试类型 |
|------|----------|----------|
| 文章 | 添加、搜索、阅读、分享 | 集成测试 |
| 日记 | 创建、编辑、搜索 | 集成测试 |
| 读书 | 搜索、添加、感悟 | 集成测试 |
| AI聊天 | 对话、搜索 | 集成测试 |
| 设置 | 主题、语言、配置 | 集成测试 |
| 备份 | 导出、导入 | 集成测试 |
| 组件 | Widget单元测试 | 单元测试 |

### 待完善的功能

- [ ] 性能测试
- [ ] 可访问性测试
- [ ] 网络异常处理测试
- [ ] 数据迁移测试
- [ ] 国际化测试

## 📁 文件说明

```
test/
├── widget_test.dart           # 单元测试
├── test_helpers.dart          # 测试工具
├── test_config.dart           # 测试配置（AI等）
integration_test/
├── basic_app_test.dart        # 基础集成测试
├── full_app_test.dart         # 完整功能测试
├── comprehensive_app_test.dart # 综合功能测试
└── test_utils.dart            # 测试辅助工具
```

## 🔧 故障排除

### 测试失败
1. 检查设备连接：`flutter devices`
2. 清理项目：`flutter clean && flutter pub get`
3. 重启adb：`adb kill-server && adb start-server`

### 集成测试问题
- 确保有设备连接或模拟器运行
- 使用较长的等待时间（15秒+）
- 检查应用是否正常启动
- 完整功能测试需要配置环境变量

### 环境变量问题
- 确认已配置 `TEST_AI_URL`、`TEST_AI_TOKEN` 和 `TEST_AI_MODEL`
- 验证API Key有效且有余额
- 检查网络连接是否正常

## 📚 相关文档

- [Flutter 测试文档](https://docs.flutter.dev/testing)
- [Integration 测试指南](https://docs.flutter.dev/testing/integration-tests)
- [Widget 测试指南](https://docs.flutter.dev/cookbook/testing/widget)
- [编码规范](./01-coding-standards.md)

## 💡 提示

- 测试应该在真实设备上运行，模拟器可能表现不同
- 保持测试独立，不要依赖测试执行顺序
- 定期更新测试数据，避免使用过时的内容
- 测试也是代码，需要保持整洁和可维护