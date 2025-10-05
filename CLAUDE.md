# Daily Satori 编码规范与项目约定

本文档定义了 Daily Satori 项目的核心编码标准、架构约束和最佳实践。

## 📚 技术栈

- **Flutter**: 3.32.x | **Dart**: 3.8.x
- **状态管理**: GetX (GetMaterialApp, Bindings, Controller + Rx)
- **本地存储**: ObjectBox (仓储模式)
- **网络**: dio, web_socket_channel
- **WebView**: flutter_inappwebview
- **AI**: openai_dart + 配置文件(assets/configs/)

## 🏗️ 系统架构

### 分层原则
- **界面层** (`app/modules/*/views`): 界面展示与用户交互
- **控制层** (`app/modules/*/controllers`): GetX Controller，状态管理与生命周期
- **绑定层** (`app/modules/*/bindings`): 依赖注入
- **服务层** (`app/services/*`): 跨模块服务
- **仓储层** (`app/repositories/*`): ObjectBox 数据访问
- **模型层** (`app/models/*`): 数据模型

### 目录结构
```
lib/app/
├── controllers/      # 基础控制器
├── modules/          # 功能模块(bindings/controllers/views)
├── services/         # 全局服务(含state/状态服务)
├── repositories/     # 数据仓库(静态方法)
├── components/       # 可复用组件
├── styles/          # 样式系统
└── routes/          # 路由配置
```


## 🎯 GetX 架构核心约束

### 1. 控制器规范
- ✅ **必须**继承 `BaseGetXController`
- ✅ **必须**使用响应式变量 `.obs`
- ❌ **禁止**直接继承 `GetxController`
- ❌ **禁止**使用普通变量管理状态

### 2. 状态管理约束
- ✅ **必须**使用状态服务管理全局状态（AppStateService, ArticleStateService, DiaryStateService）
- ✅ **必须**通过事件总线模式进行跨页面通信
- ❌ **禁止** `Get.find()` 查找其他控制器
- ❌ **禁止**静态全局变量

### 3. 数据管理架构（推荐）

| 层级 | 职责 |
|------|------|
| **Repository** | ObjectBox 查询、数据持久化 |
| **StateService** | 列表数据缓存、业务逻辑、事件通知 |
| **Controller** | UI交互、用户输入、调用Service |
| **View** | Widget渲染、Obx响应式绑定 |

### 4. 依赖注入约束
- ✅ **必须**使用现代 API: `Bindings` + `void dependencies()`
- ✅ 服务必须在 `ServiceRegistry` 注册
- ❌ **禁止**旧 API: `Binding` + `List<Bind>`

### 5. Widget 组件规范
- ✅ **推荐** `StatelessWidget` 用于纯展示组件
- ✅ 通过参数接收数据，通过回调交互
- ✅ 状态管理在父组件用 `Obx` 控制
- ❌ **避免**组件依赖特定Controller (GetView仅用于页面级)

### 6. 路由与导航
- ✅ **必须**使用 `NavigationService`
- ❌ **禁止**直接使用 `Get.toNamed()`


## 🔧 错误处理与数据访问

### 异步操作
- ✅ **必须**使用 `safeExecute()` 处理异步操作
- ✅ 统一加载状态和错误处理

### 用户反馈
- ✅ **必须**使用统一的消息方法: `showError()`, `showSuccess()`, `showLoading()`

### 数据访问
- ✅ 仓储类使用静态方法风格
- ✅ 查询必须通过仓储层
- ✅ 删除需清理关联数据

### 时间管理
- ✅ 持久化存储为 UTC
- ✅ 展示使用 `DateTimeUtils.formatDateTimeToLocal`

### 安全与隐私
- ✅ 敏感信息存储于 `SettingRepository`
- ❌ **禁止**在日志中输出 Token/口令


## 🎨 统一样式系统

### 核心原则
1. **一致性优先**: 使用统一样式系统
2. **语义化设计**: 有意义的命名
3. **主题感知**: 自动适配亮/暗色主题
4. **单一来源**: 避免重复定义

### 导入规范
```dart
// ✅ 唯一正确方式
import 'package:daily_satori/app/styles/index.dart';
```

### 基础 Tokens

#### 颜色系统 (AppColors)
```dart
// ✅ 使用主题感知方法
AppColors.getPrimary(context)
AppColors.getSurface(context)
AppColors.getOnSurfaceVariant(context)

// ❌ 禁止硬编码
Color(0xFF5E8BFF)
Colors.blue
```

#### 尺寸系统 (Dimensions)
```dart
// ✅ 间距常量
Dimensions.spacingXs/S/M/L/Xl/Xxl  // 4/8/16/24/32/48px

// ✅ 内边距预设
Dimensions.paddingPage/Card/Button/Input/ListItem

// ✅ 间隔组件
Dimensions.verticalSpacerS/M/L/Xl
Dimensions.horizontalSpacerS/M/L

// ✅ 圆角
Dimensions.radiusXs/S/M/L/Xl/Circular

// ✅ 图标尺寸
Dimensions.iconSizeXs/S/M/L/Xl/Xxl  // 12/16/20/24/32/48px
```

#### 字体系统 (AppTypography)
```dart
// 标题系列
AppTypography.headingLarge/Medium/Small  // 32/24/20px

// 副标题系列
AppTypography.titleLarge/Medium/Small    // 18/16/14px

// 正文系列
AppTypography.bodyLarge/Medium/Small     // 16/15/13px

// 标签系列
AppTypography.labelLarge/Medium/Small    // 14/12/11px

// 特殊用途
AppTypography.buttonText/appBarTitle/chipText
```

#### 透明度 (Opacities)
```dart
Opacities.extraLow/low/mediumLow/medium/mediumHigh/high/half/mediumOpaque
// 5%/10%/15%/20%/25%/30%/50%/80%
```

### 组件样式

#### 按钮 (ButtonStyles)
```dart
ButtonStyles.getPrimaryStyle(context)     // 主要按钮
ButtonStyles.getSecondaryStyle(context)   // 次要按钮
ButtonStyles.getOutlinedStyle(context)    // 轮廓按钮
ButtonStyles.getTextStyle(context)        // 文本按钮
ButtonStyles.getDangerStyle(context)      // 危险按钮
```

#### 输入框 (InputStyles)
```dart
InputStyles.getInputDecoration(context, hintText: '...')
InputStyles.getSearchDecoration(context, hintText: '...')
InputStyles.getCleanInputDecoration(context, hintText: '...')
InputStyles.getTitleInputDecoration(context, hintText: '...')
```

### StyleGuide 高级应用

```dart
// 容器装饰
StyleGuide.getPageContainerDecoration(context)
StyleGuide.getCardDecoration(context)
StyleGuide.getListItemDecoration(context)

// 状态组件
StyleGuide.getEmptyState(context, message: '...', icon: Icons.inbox)
StyleGuide.getLoadingState(context, message: '...')
StyleGuide.getErrorState(context, message: '...', onRetry: ...)

// 页面布局
StyleGuide.getStandardPageLayout(context: context, child: ...)
StyleGuide.getStandardListLayout(context: context, children: ...)
```

### 迁移指南

| 旧API (废弃) | 新API (推荐) |
|------------|------------|
| `MyFontStyle.titleLarge` | `AppTypography.titleLarge(context)` |
| `AppColors.primaryLight` | `AppColors.getPrimary(context)` |
| `ComponentStyle.cardTheme()` | `CardStyles.*` 或 `StyleGuide.*` |

### 优先级顺序
1. 优先使用 `StyleGuide` 高级方法
2. 其次使用组件样式类 (`ButtonStyles`, `InputStyles`)
3. 再次使用基础 Tokens (`Dimensions`, `AppColors`, `AppTypography`)
4. 最后才使用 `.copyWith()` 微调


## 📋 代码规范

### 命名约定
- 文件/目录: `snake_case`
- 类/枚举: `PascalCase`
- 方法/变量: `camelCase`
- 常量: `SCREAMING_SNAKE_CASE`

### Import 规范
```dart
// 1. Dart/Flutter 核心库
import 'dart:async';
import 'package:flutter/material.dart';

// 2. 第三方库
import 'package:get/get.dart';

// 3. 项目内导入(优先聚合导出)
import 'package:daily_satori/app_exports.dart';
```

## 🏆 功能模块规范

### 首页 (Home)
- 底部导航：文章、日记、读书、设置

### 文章模块 (Articles, ArticleDetail)
- 列表：分页、搜索、标签/收藏/日期筛选
- 详情：截图分享、图片管理、AI生成Markdown
- 状态共享：依赖状态服务跨页面更新

### 日记模块 (Diary)
- `DiaryEditor` 组件供读书页复用

### 读书模块 (Books) - **强约束**
- ✅ **必须**始终显示"添加感悟"悬浮按钮(FAB)
- 位置：右下角 `FloatingActionButtonLocation.endFloat`
- 图标：`Icons.edit_note` | tooltip: `添加感悟`
- 点击行为：预填模板 + 打开 `DiaryEditor`
- ❌ **禁止**在无观点时隐藏FAB

### 备份与还原
- 本地备份、归档/解档
- **图片路径恢复**：从备份恢复后自动修复路径
- 使用 `FileService.i.resolveLocalMediaPath`

### AI 能力
- 翻译、摘要、HTML→Markdown
- 配置：assets + 设置可覆盖

### 其他服务
- Web内容解析、ADBlock
- 应用升级、剪贴板监控、磁盘清理
- 分享功能

## ⚙️ 服务注册

- 新服务实现 `AppService`
- 在 `ServiceRegistry.registerAll()` 注册
- 按优先级：critical/high/normal/low
- 资源管理：Controller 中正确 dispose


## 📝 代码质量检查

### 强制执行 flutter analyze
```bash
# ✅ 每次代码修改后必须执行
flutter analyze

# ✅ 确保输出: No issues found!
```

**执行要求**：
- 修改代码后立即执行
- 修复所有 error、warning、info
- 再次执行确认无问题
- 提交前最终检查

## 🔍 检查清单

### 架构约束
- [ ] 继承 `BaseGetXController`
- [ ] 使用状态服务(不直接查找控制器)
- [ ] 使用事件总线模式
- [ ] 使用 `NavigationService` 导航
- [ ] 服务在 `ServiceRegistry` 注册

### GetX 实践
- [ ] 变量使用 `.obs`
- [ ] UI使用 `Obx()` 更新
- [ ] 依赖注入用 `Get.put()` / `Get.lazyPut()`
- [ ] 避免控制器相互查找
- [ ] 明确定义事件类型

### 代码质量
- [ ] 执行 `flutter analyze` 通过
- [ ] 异步操作用 `safeExecute()`
- [ ] 使用统一消息方法

### 样式系统
- [ ] 导入 `app/styles/index.dart`
- [ ] 使用 `Dimensions` 常量
- [ ] 使用 `AppColors.getXxx(context)`
- [ ] 使用 `AppTypography` 字体
- [ ] 使用 `ButtonStyles` / `InputStyles`
- [ ] 优先使用 `StyleGuide` 方法
- [ ] 避免硬编码数值/颜色

### 功能约束
- [ ] 读书页FAB始终显示
- [ ] 备份恢复后路径修复
- [ ] UTC存储与本地化显示
- [ ] 敏感信息不输出日志

## ⚠️ 违规后果
- 代码审查不通过
- PR被拒绝
- 需重构后重新提交
- **未执行analyze的代码直接拒绝**

## 🔄 文档维护
- 架构/服务/功能变更需同步更新
- 新增模块补充到相应章节
- 确保文档与代码一致

---

**所有开发者必须严格遵守这些约束。如有疑问，开发前讨论确认。**
