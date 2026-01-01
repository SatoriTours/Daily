# 📋 Daily Satori 编码规范

> 所有 AI 工具生成的代码必须遵循本规范。

## 📚 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Flutter 3.32.x / Dart 3.8.x |
| 状态管理 | Riverpod 3.0 + freezed |
| 本地存储 | ObjectBox |
| 导航 | go_router 14.x |
| AI | openai_dart |

## 🏗️ 项目架构

| 层级 | 路径 | 职责 |
|------|------|------|
| 界面层 | `pages/*/views` | ConsumerWidget |
| 控制层 | `pages/*/providers` | 页面级 Provider |
| 状态层 | `providers/*` | 全局状态 |
| 服务层 | `services/*` | 跨模块服务 |
| 数据层 | `data/*` | 模型 + 仓储 |

## 🎯 Riverpod 架构（核心）

> 详见 [Riverpod 最佳实践](./06-riverpod-style-guide.md)

### 必须遵守

- ✅ `@riverpod` 注解 + 代码生成
- ✅ `freezed` 定义不可变状态
- ✅ `ConsumerWidget` + `ref.watch()` 构建 UI
- ✅ 事件回调中使用 `ref.read()`
- ✅ `AsyncValue.guard()` 包装异步操作

### 严禁

- ❌ GetX 模式 (`.obs`, `Obx`, `Get.find`)
- ❌ Provider 循环依赖
- ❌ 在 `build()` 中使用 `ref.read()`

## 🎨 样式系统

> 详见 [样式指南](./04-style-guide.md)

```dart
// ✅ 唯一导入方式
import 'package:daily_satori/app/styles/index.dart';

// ✅ 使用主题感知方法
AppColors.getPrimary(context)
Dimensions.paddingCard
AppTypography.bodyMedium
ButtonStyles.getPrimaryStyle(context)

// ❌ 禁止硬编码
Color(0xFF5E8BFF)    // 禁止
EdgeInsets.all(16)   // 禁止
TextStyle(fontSize: 14)  // 禁止
```

## 📝 代码质量

### 强制约束

| 约束 | 限制 |
|------|------|
| 函数长度 | ≤ 50 行 |
| 缩进层数 | ≤ 3 层 |
| 分析检查 | `flutter analyze` 无错误 |

### 命名约定

| 类型 | 风格 | 示例 |
|------|------|------|
| 文件 | snake_case | `article_controller.dart` |
| 类 | PascalCase | `ArticleController` |
| 方法/变量 | camelCase | `sendMessage()` |
| 常量 | SCREAMING_SNAKE_CASE | `MAX_COUNT` |

### 日志规范

```dart
logger.d('[ClassName] 操作描述');  // 调试
logger.i('[ClassName] 用户操作');  // 信息
logger.w('[ClassName] 警告');      // 警告
logger.e('[ClassName] 错误', error: e);  // 错误
```

## 🔧 数据访问

```dart
// ✅ 仓储静态方法
final articles = ArticleRepository.getAll();
ArticleRepository.save(article);

// ✅ 时间管理
article.createdAt = DateTime.now().toUtc();  // 存储 UTC
DateTimeUtils.formatDateTimeToLocal(...)     // 展示本地化

// ✅ 用户反馈
showError('错误信息');
showSuccess('操作成功');
```

## ⚠️ 安全与隐私

- ✅ 敏感信息存储于 `SettingRepository`
- ❌ 禁止在日志中输出 Token/口令

## ✅ 检查清单

- [ ] Provider 使用 `@riverpod` 注解
- [ ] 状态类使用 `@freezed` 注解
- [ ] `ref.watch()` 在 build 中，`ref.read()` 在事件中
- [ ] 导入 `app/styles/index.dart`
- [ ] 无硬编码颜色/间距/字体
- [ ] 函数 ≤ 50 行，缩进 ≤ 3 层
- [ ] `flutter analyze` 无问题
