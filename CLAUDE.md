# Daily Satori 项目指南

> 本文档是 AI 编码助手的入口指南，详细规范请查阅 `docs/` 目录。

## 📖 文档索引

| 文档 | 用途 |
|------|------|
| [01-coding-standards](./docs/01-coding-standards.md) | 架构约束、代码质量规范 |
| [02-testing](./docs/02-testing.md) | 测试指南 |
| [03-app-features](./docs/03-app-features.md) | 功能模块说明 |
| [04-style-guide](./docs/04-style-guide.md) | 样式系统参考 |
| [05-i18n-guide](./docs/05-i18n-guide.md) | 国际化指南 |
| [06-riverpod-style-guide](./docs/06-riverpod-style-guide.md) | Riverpod 最佳实践 |

## 🚨 核心约束

1. **Riverpod 架构**：`@riverpod` 注解 + `freezed` 状态 + `ConsumerWidget`
2. **代码质量**：函数 ≤50 行，缩进 ≤3 层
3. **样式系统**：`import 'package:daily_satori/app/styles/index.dart';`
4. **质量检查**：修改后执行 `flutter analyze`

## 📂 项目结构

\`\`\`
lib/app/
├── pages/       # 页面模块 (views/providers/widgets)
├── providers/   # 全局状态 Providers
├── services/    # 全局服务
├── data/        # 数据层 (模型+仓储)
├── components/  # 可复用组件
├── styles/      # 样式系统
└── routes/      # 路由配置 (go_router)
\`\`\`

## ⚠️ 禁止事项

- ❌ GetX 模式 (`.obs`, `Obx`, `Get.find`)
- ❌ 硬编码颜色/间距/字体
- ❌ 日志输出敏感信息

## ✅ 代码校验（每次修改后必须执行）

```bash
# 1. 静态分析 - 检查语法错误和代码问题
flutter analyze

# 2. 代码生成 - 如果修改了 Provider 或 freezed 模型
flutter pub run build_runner build --delete-conflicting-outputs

# 3. 格式化 - 统一代码风格
dart format .
```

**必须确保 `flutter analyze` 输出 `No issues found!` 后才能提交代码。**

## 📝 const 构造函数规则

由于项目使用 freezed 管理的模型中包含非 const 类型的字段（如 ArticleModel、BookModel、DiaryModel 等），
测试文件中无法对这些模型使用 `const` 构造函数。此限制已在 `analysis_options.yaml` 中配置忽略，
无需手动修复此类 info 级别警告。

## 📱 Android 构建与部署（每次修改代码后自动执行）

```bash
# 编译并安装到已连接的设备
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug

# 启动 App
adb shell am start -n com.dailysatori/.MainActivity
```

**修改代码后必须执行以上步骤，确保变更生效到设备上。**
