# Daily Satori 项目指南（Claude Code）

本文档是 Claude Code 的项目入口指南，帮助 AI 快速理解项目并正确编写代码。

---

## 📖 必读文档

> **重要**：在编写代码前，必须阅读以下文档！

| 文档 | 说明 | 何时阅读 |
|------|------|----------|
| [编码规范](./docs/01-coding-standards.md) | 统一编码标准、架构约束、最佳实践 | **每次编写代码前** |
| [Riverpod 指南](./docs/06-riverpod-style-guide.md) | Riverpod + freezed 最佳实践 | 状态管理开发时 |
| [应用功能](./docs/APP_FEATURES.md) | 完整的功能模块说明和约束 | **修改具体页面功能时** |
| [样式指南](./docs/STYLE_GUIDE.md) | 样式系统快速参考 | 编写 UI 代码时 |
| [国际化指南](./docs/I18N_GUIDE.md) | 多语言开发指南 | 添加文本时 |
| [迁移文档](./docs/RIVERPOD_MIGRATION.md) | GetX → Riverpod 迁移进度 | 了解迁移状态时 |

---

## 🚨 核心约束（必须遵守）

### 1. 架构约束

- ✅ 使用 `@riverpod` 注解 + 代码生成定义 providers
- ✅ 使用 `ref.watch()` 进行响应式读取，`ref.read()` 进行一次性读取
- ✅ 使用 freezed 定义不可变状态模型
- ✅ 使用状态 providers 管理全局状态（articleStateProvider, diaryStateProvider 等）
- ❌ 禁止 `.obs`、`Obx()`、`Get.find()`、`Get.toNamed()` 等 GetX 模式
- ❌ 禁止跨 provider 直接调用，使用 `ref.watch()` / `ref.read()`

### 2. 代码质量

- ✅ 每个函数不超过 **50 行**
- ✅ 代码缩进不超过 **3 层**
- ✅ 异步操作使用 `AsyncValue.guard()` 包装
- ✅ 修改后必须执行 `flutter pub run build_runner build`（如果有代码生成）
- ✅ 修改后必须执行 `flutter analyze`

### 3. 样式系统

- ✅ 必须导入 `import 'package:daily_satori/app/styles/index.dart';`
- ❌ 禁止硬编码颜色、间距、字体
- ✅ 优先使用 `StyleGuide` > `ButtonStyles` > `Dimensions`

### 4. 功能约束

- ✅ **读书页 FAB 必须始终显示**（查看 APP_FEATURES.md）
- ✅ 备份恢复后必须修复图片路径
- ✅ 时间存储 UTC，展示转本地
- ❌ 禁止在日志中输出敏感信息

---

## 📂 项目结构

```
lib/app/
├── pages/            # 功能页面(views → ConsumerWidget)
├── providers/        # Riverpod providers (状态管理)
├── services/         # 全局服务(AI/Web服务等)
├── data/             # 数据层(模型+仓储，按实体分组)
├── components/       # 可复用组件(统一导出: components/index.dart)
├── styles/           # 样式系统
├── utils/            # 工具类(i18n扩展等)
└── routes/           # 路由配置(go_router)
```

---

## 🔧 开发工作流

### 修改页面功能时

1. **先阅读** `docs/APP_FEATURES.md` 中对应模块的说明
2. 理解数据模型和约束条件
3. 编写代码
4. 执行 `flutter analyze` 确保无问题

### 编写 UI 代码时

1. 导入样式系统 `import 'package:daily_satori/app/styles/index.dart';`
2. 查阅 `docs/STYLE_GUIDE.md` 获取样式参考
3. 遵循组件拆分原则（每个函数 ≤ 50 行）

### 添加新功能时

1. 阅读 `docs/01-coding-standards.md` 了解架构约束
2. 阅读 `docs/06-riverpod-style-guide.md` 了解 Riverpod 最佳实践
3. 更新 `docs/APP_FEATURES.md` 记录新功能
4. 创建对应的 provider（使用 `@riverpod` 注解）

---

## ⚠️ 常见错误提醒

```dart
// ❌ 错误示例
class MyController extends GetxController { ... }  // 应使用 @riverpod 注解
final isLoading = false.obs;  // 应使用 freezed 状态模型
Obx(() => Text(...))  // 应使用 ConsumerWidget + ref.watch()
Get.find<OtherController>()  // 应使用 ref.read(otherControllerProvider)
Get.toNamed('/route')  // 应使用 go_router: context.go('/route')
Color(0xFF5E8BFF)  // 应使用 AppColors.getPrimary(context)
EdgeInsets.all(16)  // 应使用 Dimensions.paddingCard

// ✅ 正确示例
@riverpod
class MyController extends _$MyController { }

@freezed
class MyControllerState with _$MyControllerState {
  const factory MyControllerState({
    @Default(false) bool isLoading,
  }) = _MyControllerState;
}

class MyView extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(myControllerProvider);
    return Text('${state.isLoading}');
  }
}

AppColors.getPrimary(context)
Dimensions.paddingCard
ref.read(articleStateProvider)  // 读取其他 provider
context.go('/article/$id')  // 使用 go_router
```

---

## 📝 代码质量检查

```bash
# 每次修改 providers 后必须执行代码生成
flutter pub run build_runner build --delete-conflicting-outputs

# 每次修改后必须执行静态分析
flutter analyze

# 确保输出: No issues found!
```

---

## 🔗 快速链接

- [编码规范](./docs/01-coding-standards.md) - Riverpod 架构标准
- [Riverpod 指南](./docs/06-riverpod-style-guide.md) - 最佳实践
- [迁移文档](./docs/RIVERPOD_MIGRATION.md) - GetX → Riverpod 迁移进度
- [应用功能](./docs/APP_FEATURES.md) - 功能说明
- [样式指南](./docs/STYLE_GUIDE.md) - 样式参考
- [国际化](./docs/I18N_GUIDE.md) - 多语言

---

**遵守规范，写出高质量代码！**
