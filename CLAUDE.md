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
