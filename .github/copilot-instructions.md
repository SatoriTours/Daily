# Daily Satori 编码规范（GitHub Copilot）

本文件是 GitHub Copilot 的项目入口指南，帮助 AI 快速理解项目并正确编写代码。

---

## 📖 必读文档

> **重要**：在编写代码前，必须阅读以下文档！

| 文档 | 说明 | 何时阅读 |
|------|------|----------|
| [编码规范](../docs/01-coding-standards.md) | 统一编码标准、架构约束、最佳实践 | **每次编写代码前** |
| [应用功能](../docs/03-app-features.md) | 完整的功能模块说明和约束 | **修改具体页面功能时** |
| [样式指南](../docs/04-style-guide.md) | 样式系统快速参考 | 编写 UI 代码时 |
| [国际化指南](../docs/05-i18n-guide.md) | 多语言开发指南 | 添加文本时 |
| [Riverpod 规范](../docs/06-riverpod-style-guide.md) | Riverpod 状态管理规范 | 使用状态管理时 |

---

## 🚨 核心约束（必须遵守）

### 1. 架构约束（Riverpod）

- ✅ 使用 `@riverpod` 注解定义 Provider
- ✅ 使用 `ConsumerWidget` / `ConsumerStatefulWidget` 构建 UI
- ✅ 使用 `ref.watch()` 监听状态，`ref.read()` 触发操作
- ✅ 使用 `freezed` 定义不可变状态类
- ❌ 禁止跨 Provider 直接访问（使用 `ref.watch/read` 代替）
- ❌ 禁止使用静态全局变量

### 2. 代码质量

- ✅ 每个函数不超过 **50 行**
- ✅ 代码缩进不超过 **3 层**
- ✅ 异步操作必须处理错误（try-catch）
- ✅ 修改后必须执行 `flutter analyze`

### 3. 样式系统

- ✅ 必须导入 `import 'package:daily_satori/app/styles/index.dart';`
- ❌ 禁止硬编码颜色、间距、字体
- ✅ 优先使用 `StyleGuide` > `ButtonStyles` > `Dimensions`

### 4. 功能约束

- ✅ **读书页 FAB 必须始终显示**（查看 03-app-features.md）
- ✅ 备份恢复后必须修复图片路径
- ✅ 时间存储 UTC，展示转本地
- ❌ 禁止在日志中输出敏感信息

---

## 📂 项目结构

```
lib/app/
├── pages/            # 功能页面(views/widgets)
├── providers/        # Riverpod providers (状态管理)
├── services/         # 全局服务(单例模式)
├── data/             # 数据层(模型+仓储，按实体分组)
├── components/       # 可复用组件(统一导出: components/index.dart)
├── styles/           # 样式系统
├── utils/            # 工具类(i18n扩展等)
├── navigation/       # 导航配置
└── config/           # 应用配置
```

---

## 🔧 开发工作流

### 修改页面功能时

1. **先阅读** `docs/03-app-features.md` 中对应模块的说明
2. 理解数据模型和约束条件
3. 编写代码
4. 执行 `flutter analyze` 确保无问题

### 编写 UI 代码时

1. 导入样式系统 `import 'package:daily_satori/app/styles/index.dart';`
2. 查阅 `docs/04-style-guide.md` 获取样式参考
3. 遵循组件拆分原则（每个函数 ≤ 50 行）

### 添加新功能时

1. 阅读 `docs/01-coding-standards.md` 了解架构约束
2. 更新 `docs/03-app-features.md` 记录新功能
3. 确保新服务在 `ServiceRegistry` 注册

---

## ⚠️ 常见错误提醒

```dart
// ❌ 错误示例
Color(0xFF5E8BFF)  // 应使用 AppColors.getPrimary(context)
EdgeInsets.all(16)  // 应使用 Dimensions.paddingCard
StatelessWidget  // 需要状态时应使用 ConsumerWidget

// ✅ 正确示例
class MyWidget extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(myProvider);
    return Text(state.value);
  }
}
AppColors.getPrimary(context)
Dimensions.paddingCard
```

---

## 📝 代码质量检查

```bash
# 每次修改后必须执行
flutter analyze

# 确保输出: No issues found!
```

---

## 🔗 快速链接

- [编码规范](../docs/01-coding-standards.md) - 统一标准
- [应用功能](../docs/03-app-features.md) - 功能说明
- [样式指南](../docs/04-style-guide.md) - 样式参考
- [国际化](../docs/05-i18n-guide.md) - 多语言
- [Riverpod 规范](../docs/06-riverpod-style-guide.md) - 状态管理

---

**遵守规范，写出高质量代码！**
