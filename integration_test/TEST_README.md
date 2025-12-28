# 集成测试使用指南

## 📋 测试概览

本项目包含完整的自动化集成测试套件，覆盖所有核心功能模块。

## 🎯 测试文件列表

### 综合测试
| 测试文件 | 说明 | 推荐使用 |
|---------|------|---------|
| `all_features_test.dart` | 全功能自动化测试（7大模块） | ✅ 日常开发 |
| `full_app_test.dart` | 完整功能测试流程（已优化） | ✅ 提交前验证 |

### 模块专项测试
| 测试文件 | 测试内容 |
|---------|---------|
| `article_collection_test.dart` | 文章收藏完整流程 |
| `diary_test.dart` | 日记创建、编辑、搜索、Markdown |
| `books_test.dart` | 书籍搜索、添加、感悟记录 |
| `settings_test.dart` | 主题、语言、AI配置、Web服务 |
| `backup_restore_test.dart` | 备份设置、自动备份、恢复 |

### 测试工具
| 文件 | 说明 |
|------|------|
| `test_config.dart` | 测试配置（URL、关键词、等待时间） |
| `test_utils.dart` | 测试辅助工具类 |
| `test_ai_bootstrap.dart` | AI配置初始化 |

## 🚀 快速开始

### 1. 日常开发（推荐）

```bash
# 快速检查（无需设备）
./test.sh quick

# 全功能测试（需要Android模拟器）
./test.sh all
```

### 2. 提交代码前

```bash
# 完整验证（包括数据初始化）
./test.sh full

# 或者运行所有模块专项测试
./test.sh modules
```

### 3. 测试单个模块

```bash
./test.sh diary    # 测试日记模块
./test.sh books    # 测试读书模块
./test.sh settings # 测试设置模块
./test.sh backup   # 测试备份恢复
./test.sh article  # 测试文章收藏
```

## ⚙️ 配置AI功能测试（可选）

如果要测试AI相关功能，需要配置环境变量：

```bash
# 设置环境变量
export TEST_AI_URL="https://api.openai.com/v1/chat/completions"
export TEST_AI_TOKEN="sk-your-api-key-here"
export TEST_AI_MODEL="gpt-3.5-turbo"

# 验证环境变量
echo $TEST_AI_TOKEN
echo $TEST_AI_URL
echo $TEST_AI_MODEL

# 然后运行测试（环境变量会自动通过--dart-define传递）
./test.sh all
```

**重要提示**:
- 环境变量需要通过`--dart-define`传递给Flutter，test.sh已经自动处理
- 如果环境变量未设置，测试会跳过AI相关功能，不会失败
- 支持OpenAI兼容的API（如DeepSeek、Azure OpenAI等）

**示例配置**:
```bash
# OpenAI
export TEST_AI_URL="https://api.openai.com/v1"
export TEST_AI_TOKEN="sk-..."
export TEST_AI_MODEL="gpt-4"

# DeepSeek
export TEST_AI_URL="https://api.deepseek.com"
export TEST_AI_TOKEN="sk-..."
export TEST_AI_MODEL="deepseek-chat"

# Azure OpenAI
export TEST_AI_URL="https://your-resource.openai.azure.com/"
export TEST_AI_TOKEN="your-api-key"
export TEST_AI_MODEL="gpt-35-turbo"
```

## 📊 测试覆盖的功能

### 📰 文章模块
- ✅ 添加文章（剪贴板URL）
- ✅ 文章搜索
- ✅ 文章详情查看
- ✅ 刷新、删除功能
- ✅ 收藏/取消收藏

### 📔 日记模块
- ✅ 创建日记（Markdown支持）
- ✅ 编辑日记
- ✅ 搜索日记
- ✅ 删除日记

### 📚 读书模块
- ✅ 添加书籍（豆瓣搜索）
- ✅ 添加读书感悟
- ✅ 编辑感悟
- ✅ 搜索功能
- ✅ FAB按钮始终可见验证

### ⚙️ 设置模块
- ✅ 主题切换（浅色/深色/跟随系统）
- ✅ 语言切换（中文/English）
- ✅ AI配置管理
- ✅ Web服务配置
- ✅ 存储管理
- ✅ 插件中心

### 💾 备份恢复
- ✅ 备份设置页面
- ✅ 自动备份开关
- ✅ 备份路径配置
- ✅ 手动备份功能
- ✅ 恢复功能入口
- ✅ 备份历史管理

## 🛠️ 故障排除

### 测试失败：未检测到设备

```bash
# 检查设备连接
flutter devices

# 启动Android模拟器
flutter emulators --launch <emulator_id>
```

### 测试失败：剪贴板检测不到

这是正常情况，测试会自动切换到手动添加模式。

### 测试警告：widget点击被遮挡

测试会自动处理这些警告，不会影响测试结果。

## 📝 测试结果解读

### 成功示例
```
========================================
🚀 开始完整功能测试
========================================

📝 [步骤1] 测试文章保存功能...
  ✓ 已设置剪贴板内容
  ✓ 已保存文章
✅ [步骤1] 文章保存测试完成

01:43 +1: All tests passed!
```

### 失败示例
```
❌ [步骤1] 文章保存测试失败: ...
Stack trace: ...
```

## 🔧 测试最佳实践

1. **开发新功能时**: 先运行 `./test.sh quick` 确保代码分析通过
2. **修改完成后**: 运行 `./test.sh all` 验证所有功能
3. **提交代码前**: 运行 `./test.sh modules` 确保每个模块都正常
4. **遇到网络问题**: 某些测试可能重试后会成功

## 📚 相关文档

- [编码规范](../docs/CODING_STANDARDS.md)
- [应用功能](../docs/APP_FEATURES.md)
- [样式指南](../docs/STYLE_GUIDE.md)
- [国际化指南](../docs/I18N_GUIDE.md)

## 🎉 测试成功标准

- ✅ `flutter analyze` 无问题
- ✅ 单元测试全部通过
- ✅ 集成测试在Android模拟器上通过
- ✅ 所有核心功能正常工作
