# Daily Satori 编码规范（GitHub Copilot）

本文件定义了 Daily Satori 项目的完整编码规范、架构约束和最佳实践。

> **重要**：本规范与 `CLAUDE.md` 保持一致，确保不同工具生成的代码遵循相同标准。

## 📚 技术栈

- **Flutter**: 3.32.x | **Dart**: 3.8.x
- **状态管理**: GetX (GetMaterialApp, Bindings, Controller + Rx)
- **本地存储**: ObjectBox (仓储模式)
- **网络**: dio, web_socket_channel
- **WebView**: flutter_inappwebview
- **AI**: openai_dart + 配置文件

## 🏯 项目架构

### 分层原则
```
lib/app/
├── core/             # 核心基础类(base_getx_controller等)
├── pages/            # 功能页面(bindings/controllers/views)
├── services/         # 全局服务(含state/状态服务)
├── data/             # 数据层(模型+仓储，按实体分组)
├── components/       # 可复用组件
├── styles/           # 样式系统
├── utils/            # 工具类(含i18n扩展等)
└── routes/           # 路由配置
```

## 🎯 GetX 架构核心约束

### 1. 控制器规范
```dart
// ✅ 必须继承 BaseGetXController
class MyController extends BaseGetXController {
  // ✅ 使用响应式变量
  final count = 0.obs;
  final isLoading = false.obs;

  // ✅ 使用 safeExecute 处理异步
  Future<void> loadData() async {
    await safeExecute(() async {
      // 异步逻辑...
    });
  }
}

// ❌ 禁止直接继承 GetxController
// ❌ 禁止使用普通变量管理状态
```

### 2. 状态管理约束
- ✅ **必须**使用状态服务管理全局状态
- ✅ **必须**通过事件总线模式进行跨页面通信
- ❌ **禁止** `Get.find()` 查找其他控制器
- ❌ **禁止**静态全局变量

### 3. Widget 组件规范
```dart
// ✅ 推荐：纯展示组件使用 StatelessWidget
class MyCard extends StatelessWidget {
  final String title;
  final VoidCallback? onTap;

  const MyCard({required this.title, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        title: Text(title),
        onTap: onTap,
      ),
    );
  }
}

// ✅ 父组件使用 Obx 控制状态
Obx(() => MyCard(
  title: controller.title.value,
  onTap: controller.handleTap,
))

// ❌ 避免组件依赖特定 Controller
```

### 4. 路由与导航
```dart
// ✅ 推荐：直接使用 GetX 路由
logger.i('[Navigation] 导航到文章详情');
Get.toNamed(Routes.articleDetail, arguments: articleId);

// ✅ 如需复杂逻辑，在 Controller 中封装
class ArticleController extends BaseGetXController {
  void openArticle(Article article) {
    // 权限检查
    if (article.isLocked && !hasPermission) {
      showError('需要权限');
      return;
    }

    // 埋点统计
    logger.i('[Navigation] 打开文章: ${article.id}');

    // 导航
    Get.toNamed(Routes.articleDetail, arguments: article);
  }
}

// ❌ 避免：没有实际价值的简单包装
NavigationService.i.toNamed(...); // 如果只是转发，就是多余的
```

### 5. 依赖注入
```dart
// ✅ 使用当前推荐 API
class MyBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut(() => MyController())];
  }
}

// ❌ 禁止已废弃 API
class MyBinding extends Bindings { // 禁止
  @override
  void dependencies() { // 禁止
    Get.lazyPut(() => MyController()); // 禁止
  }
}
```

## 🔧 错误处理与数据访问

### 异步操作
```dart
// ✅ 必须使用 safeExecute
Future<void> fetchData() async {
  await safeExecute(() async {
    final data = await repository.getData();
    items.value = data;
  });
}

// ❌ 禁止相信手动处理异常
```

### 用户反馈
```dart
// ✅ 使用统一消息方法
showError('错误信息');
showSuccess('操作成功');
showLoading('加载中...');
```

### 数据访问
```dart
// ✅ 仓储类使用静态方法
class ArticleRepository {
  static List<Article> getAll() {
    return objectbox.articleBox.getAll();
  }

  static void save(Article article) {
    objectbox.articleBox.put(article);
  }
}

// ✅ 查询必须通过仓储层
final articles = ArticleRepository.getAll();
```

### 时间管理
```dart
// ✅ 持久化存储为 UTC
article.createdAt = DateTime.now().toUtc();

// ✅ 展示使用本地化
Text(DateTimeUtils.formatDateTimeToLocal(article.createdAt))
```

## 💎 统一样式系统

### 导入规范
```dart
// ✅ 唯一正确方式
import 'package:daily_satori/app/styles/index.dart';
```

### 颜色系统
```dart
// ✅ 使用主题感知方法
AppColors.getPrimary(context)
AppColors.getSurface(context)
AppColors.getOnSurfaceVariant(context)

// ❌ 禁止硬编码
Color(0xFF5E8BFF)  // 禁止
Colors.blue        // 禁止
```

### 尺寸系统
```dart
// ✅ 使用标准间距
Dimensions.spacingXs/S/M/L/Xl/Xxl  // 4/8/16/24/32/48px

// ✅ 使用内边距预设
Dimensions.paddingPage/Card/Button/Input

// ✅ 使用间隔组件
Dimensions.verticalSpacerS/M/L
Dimensions.horizontalSpacerS/M/L

// ✅ 使用圆角
Dimensions.radiusXs/S/M/L/Xl

// ❌ 禁止硬编码
EdgeInsets.all(16)  // 禁止
BorderRadius.circular(8)  // 禁止
```

### 字体系统
```dart
// ✅ 使用 AppTypography
AppTypography.headingLarge/Medium/Small  // 32/24/20px
AppTypography.titleLarge/Medium/Small    // 18/16/14px
AppTypography.bodyLarge/Medium/Small     // 16/15/13px
AppTypography.labelLarge/Medium/Small    // 14/12/11px

// ❌ 禁止硬编码
TextStyle(fontSize: 14)  // 禁止
```

### 组件样式
```dart
// ✅ 使用 ButtonStyles
ButtonStyles.getPrimaryStyle(context)
ButtonStyles.getSecondaryStyle(context)
ButtonStyles.getOutlinedStyle(context)

// ✅ 使用 InputStyles
InputStyles.getInputDecoration(context, hintText: '...')
InputStyles.getSearchDecoration(context, hintText: '...')

// ✅ 优先使用 StyleGuide
StyleGuide.getPageContainerDecoration(context)
StyleGuide.getCardDecoration(context)
StyleGuide.getEmptyState(context, message: '...')
```

## 📝 代码规范

### 命名约定
```dart
// 文件/目录: snake_case
ai_chat_controller.dart
user_profile/

// 类/枚举: PascalCase
class ArticleController {}
enum MessageType {}

// 方法/变量: camelCase
void sendMessage() {}
final userName = '';

// 常量: SCREAMING_SNAKE_CASE
const MAX_RETRY_COUNT = 3;
```

### Import 规范
```dart
// 1. Dart/Flutter 核心库
import 'dart:async';
import 'package:flutter/material.dart';

// 2. 第三方库
import 'package:get/get.dart';
import 'package:objectbox/objectbox.dart';

// 3. 项目内导入(优先聚合导出)
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/index.dart';
```

## 🎯 代码质量强制约束

### 1. 函数长度限制
- ✅ **每个函数/方法不超过 50 行代码**
- ✅ **代码缩进不超过 3 层**
- 超过限制时必须拆分为多个小函数

### 2. 函数拆分原则

#### 基本原则
- 每个函数只做一件事
- 函数名清晰表达意图
- 避免副作用
- 保持抽象层次一致

#### 提取方法 (Extract Method)
将复杂逻辑拆分为独立的小函数：

```dart
// ❌ 错误：函数过长
Widget _buildContent(BuildContext context) {
  return Container(
    // 100+ 行的复杂逻辑...
  );
}

// ✅ 正确：拆分为多个小函数
Widget _buildContent(BuildContext context) {
  return Container(
    decoration: _buildDecoration(context),
    child: Column(
      children: [
        _buildHeader(context),
        _buildBody(context),
        _buildFooter(context),
      ],
    ),
  );
}

BoxDecoration _buildDecoration(BuildContext context) {
  return BoxDecoration(
    color: AppColors.getSurface(context),
    borderRadius: BorderRadius.circular(Dimensions.radiusS),
  );
}
```

#### 缩进控制
使用提前 return 和提取函数避免深层嵌套：

```dart
// ❌ 错误：缩进过深（4层+）
if (condition1) {
  if (condition2) {
    for (var item in list) {
      if (item.isValid) {
        // 第4层缩进...
      }
    }
  }
}

// ✅ 正确：提前 return，提取函数
if (!condition1 || !condition2) return;

for (var item in list) {
  _processItem(item);
}

void _processItem(Item item) {
  if (!item.isValid) return;
  // 处理逻辑...（最多3层缩进）
}
```

### 3. 日志规范

#### 必须添加日志的场景
- 用户操作（点击、输入、导航）
- 状态变化（加载、完成、错误）
- 关键业务逻辑执行
- 异步操作开始和结束
- 错误和异常情况

#### 日志格式
```dart
// 使用 LoggerService
import 'package:daily_satori/app/services/logger_service.dart';

// 调试信息
logger.d('[ClassName] 操作描述: 关键信息');

// 普通信息
logger.i('[ClassName] 用户操作: 操作详情');

// 警告信息
logger.w('[ClassName] 警告: 警告详情');

// 错误信息
logger.e('[ClassName] 错误: 错误详情', error: e, stackTrace: st);
```

#### 日志示例
```dart
class ChatController extends BaseGetXController {
  void sendMessage(String content) {
    logger.i('[ChatController] 发送消息: ${content.substring(0, min(50, content.length))}...');

    if (content.trim().isEmpty) {
      logger.w('[ChatController] 消息为空，忽略发送');
      return;
    }

    // 业务逻辑...
  }

  @override
  void onInit() {
    super.onInit();
    logger.d('[ChatController] 初始化');
  }

  @override
  void dispose() {
    logger.d('[ChatController] 释放资源');
    super.dispose();
  }
}
```

### 4. 文档注释规范

#### 必须添加文档注释
- 所有 public 类
- 所有 public 方法/函数
- 所有 public 属性（复杂的）
- 复杂的业务逻辑

#### 注释格式
```dart
/// 类的简要描述
///
/// 类的详细说明，可以多行
/// 说明类的用途、使用场景等
class MyWidget extends StatelessWidget {
  // ========================================================================
  // 属性
  // ========================================================================

  /// 属性的描述
  /// 说明属性的用途和注意事项
  final String title;

  /// 回调函数
  /// 当用户点击时触发
  final VoidCallback? onTap;

  // ========================================================================
  // UI构建
  // ========================================================================

  /// 构建主要内容
  ///
  /// 根据 [title] 显示标题
  /// 如果提供了 [onTap]，则可以点击
  Widget _buildContent(BuildContext context) {
    // ...
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 获取背景颜色
  ///
  /// 根据主题模式返回对应的颜色
  Color _getBackgroundColor(BuildContext context) {
    // ...
  }
}
```

#### 部分标记（Section Markers）
使用部分标记组织代码结构：

```dart
// ========================================================================
// 属性
// ========================================================================

// ========================================================================
// 生命周期
// ========================================================================

// ========================================================================
// 事件处理
// ========================================================================

// ========================================================================
// UI构建
// ========================================================================

// ========================================================================
// 辅助方法
// ========================================================================
```

### 5. Flutter 最佳实践

#### Widget 构建原则
```dart
// ✅ 使用 const 构造函数
const Text('Hello');
const SizedBox(height: 16);

// ✅ 使用 const 构造器
class MyWidget extends StatelessWidget {
  const MyWidget({super.key});
}

// ✅ 提取常量 Widget
static const _emptyBox = SizedBox.shrink();
```

#### 状态管理最佳实践
```dart
// ✅ StatelessWidget 用于纯展示
class UserCard extends StatelessWidget {
  final User user;
  const UserCard({required this.user});
}

// ✅ StatefulWidget 仅用于组件内部状态
class ExpandableCard extends StatefulWidget {
  // 只管理展开/折叠状态
}

// ✅ GetX 用于页面级状态
class ArticleListView extends GetView<ArticleController> {
  // 使用 controller 管理页面状态
}
```

#### 性能优化
```dart
// ✅ 使用 ListView.builder 而非 ListView
ListView.builder(
  itemCount: items.length,
  itemBuilder: (context, index) => ItemWidget(items[index]),
);

// ✅ 使用 const 减少重建
const Divider();
const SizedBox(height: 8);

// ✅ 避免在 build 中创建对象
final _textStyle = TextStyle(fontSize: 14); // 在 build 外部

// ❌ 禁止在 build 中创建
Widget build(BuildContext context) {
  final style = TextStyle(fontSize: 14); // 禁止
}
```

### 6. 代码组织

#### 类成员顺序
1. 常量
2. 静态变量
3. 实例变量（属性）
4. 构造函数
5. 生命周期方法（initState, dispose 等）
6. 公共方法
7. 事件处理方法
8. UI构建方法（build, _buildXxx）
9. 私有辅助方法

#### Widget 拆分原则
```dart
// ✅ 正确：将大 Widget 拆分为小 Widget
class MyPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(context),
      body: _buildBody(context),
    );
  }

  Widget _buildAppBar(BuildContext context) {
    return AppBar(
      title: _buildAppBarTitle(context),
      actions: _buildAppBarActions(context),
    );
  }

  Widget _buildBody(BuildContext context) {
    return Column(
      children: [
        _buildHeader(context),
        Expanded(child: _buildContent(context)),
        _buildFooter(context),
      ],
    );
  }

  // 每个函数都很简洁，不超过50行
}
```

### 6. 样式系统约束

```dart
// ✅ 必须使用统一样式系统
import 'package:daily_satori/app/styles/index.dart';

// ✅ 使用主题感知颜色
AppColors.getPrimary(context)
AppColors.getSurface(context)

// ✅ 使用标准间距
Dimensions.spacingS/M/L
Dimensions.paddingPage

// ✅ 使用标准字体
AppTypography.titleLarge
AppTypography.bodyMedium

// ❌ 禁止硬编码
Color(0xFF5E8BFF)
EdgeInsets.all(16)
TextStyle(fontSize: 14)
```

### 7. GetX 架构约束

```dart
// ✅ 必须继承 BaseGetXController
class MyController extends BaseGetXController {
  // ✅ 使用响应式变量
  final count = 0.obs;

  // ✅ 使用 safeExecute 处理异步
  Future<void> loadData() async {
    await safeExecute(() async {
      // 异步逻辑...
    });
  }
}

// ✅ UI 使用 Obx 更新
Obx(() => Text('Count: ${controller.count.value}'))

// ❌ 禁止 Get.find() 查找其他控制器
// ✅ 直接使用 GetX 路由
```

## 🔍 代码审查检查清单

### 架构约束
- [ ] 继承 `BaseGetXController`
- [ ] 使用状态服务(不直接查找控制器)
- [ ] 使用事件总线模式
- [ ] 直接使用 GetX 路由（Get.toNamed/back/offAllNamed）
- [ ] 导航操作添加了日志记录
- [ ] 服务在 `ServiceRegistry` 注册
- [ ] 使用 `Binding` + `List<Bind>` 依赖注入

### GetX 实践
- [ ] 变量使用 `.obs`
- [ ] UI使用 `Obx()` 更新
- [ ] 依赖注入用 `Bind.lazyPut()`
- [ ] 避免控制器相互查找
- [ ] 明确定义事件类型

### 代码质量
- [ ] 执行 `flutter analyze` 通过
- [ ] 异步操作用 `safeExecute()`
- [ ] 使用统一消息方法
- [ ] **每个函数不超过 50 行**
- [ ] **代码缩进不超过 3 层**
- [ ] 添加了适当的日志
- [ ] 添加了文档注释
- [ ] 使用了部分标记组织代码

### 样式系统
- [ ] 导入 `app/styles/index.dart`
- [ ] 使用 `Dimensions` 常量
- [ ] 使用 `AppColors.getXxx(context)`
- [ ] 使用 `AppTypography` 字体
- [ ] 使用 `ButtonStyles` / `InputStyles`
- [ ] 优先使用 `StyleGuide` 方法
- [ ] 避免硬编码数值/颜色

### Flutter 最佳实践
- [ ] 使用 `const` 构造函数
- [ ] StatelessWidget 用于纯展示
- [ ] 使用 ListView.builder 而非 ListView
- [ ] 避免在 build 中创建对象
- [ ] 正确处理资源释放 (dispose)

### 安全与隐私
- [ ] 敏感信息不输出日志
- [ ] UTC存储与本地化显示
- [ ] 正确处理用户数据

## ⚠️ 违规后果

- 代码审查不通过
- PR被拒绝
- 需重构后重新提交
- **未执行 analyze 的代码直接拒绝**

## 📚 参考文档

- **完整规范**: 项目根目录 `CLAUDE.md`
- **架构设计**: 查看 CLAUDE.md 中的系统架构章节
- **功能模块**: 查看 CLAUDE.md 中的功能模块规范

## 🔄 文档同步

本文件与 `CLAUDE.md` 保持同步更新，确保：
- GitHub Copilot 生成的代码遵循相同规范
- Claude Code 生成的代码遵循相同规范
- 所有开发者使用一致的编码标准

---

**这些规范是强制性的，所有生成的代码必须遵守。**
