# 📚 Daily Satori 项目文档

本目录包含 Daily Satori 项目的详细文档和指南。

## 📋 文档目录

| 文档 | 说明 | 优先级 |
|------|------|--------|
| [`01-coding-standards.md`](./01-coding-standards.md) | 📋 **编码规范** - 统一编码标准、架构约束、最佳实践 | ⭐⭐⭐ |
| [`02-testing.md`](./02-testing.md) | 🧪 **测试指南** - 测试套件运行和维护指南 | ⭐⭐⭐ |
| [`03-app-features.md`](./03-app-features.md) | 📱 **应用功能说明** - 完整的功能模块介绍 | ⭐⭐ |
| [`04-style-guide.md`](./04-style-guide.md) | 🎨 **样式系统指南** - 颜色、间距、字体等样式快速参考 | ⭐⭐ |
| [`05-i18n-guide.md`](./05-i18n-guide.md) | 🌐 **国际化指南** - 多语言开发使用说明 | ⭐ |

## 📖 文档说明

### 01-coding-standards.md（核心规范）
**项目的统一编码规范**，被 CLAUDE.md 和 copilot-instructions.md 共同引用，包含：
- 技术栈和项目架构
- GetX 架构约束
- 错误处理与数据访问
- 样式系统规范
- 代码质量约束（函数长度、缩进等）
- Flutter 最佳实践
- 检查清单

### 02-testing.md（测试指南）
**测试套件的完整使用指南**，包含：
- 快速测试、集成测试、完整测试等不同级别
- 环境配置和设备设置
- 测试覆盖功能说明
- 故障排除和调试方法
- 最佳实践和维护建议

### 03-app-features.md（功能说明）
**应用功能的完整说明文档**，包含：
- 核心功能模块详解（文章、日记、读书、AI）
- 各模块的实现约束和注意事项
- 数据模型和服务架构说明
- AI 助手修改代码时的重要参考

### 04-style-guide.md（样式系统）
**样式系统快速参考**，包含：
- 颜色系统（AppColors）
- 尺寸系统（Dimensions）
- 字体系统（AppTypography）
- 组件样式（ButtonStyles、InputStyles）
- StyleGuide 高级应用

### 05-i18n-guide.md（国际化）
**国际化开发指南**，包含：
- YAML 配置文件结构
- `.t` 扩展方法使用方式
- 最佳实践和注意事项

## 🚀 快速开始

1. 📋 阅读 [`01-coding-standards.md`](./01-coding-standards.md) 了解编码规范
2. 🧪 查看 [`02-testing.md`](./02-testing.md) 学习如何运行测试
3. 📱 阅读 [`03-app-features.md`](./03-app-features.md) 了解应用功能
4. 🎨 查看 [`04-style-guide.md`](./04-style-guide.md) 学习样式规范
5. 🌐 参考 [`05-i18n-guide.md`](./05-i18n-guide.md) 进行多语言开发

## 📝 文档维护

- 修改编码规范时请更新 `01-coding-standards.md`
- 添加新功能时请同步更新 `03-app-features.md`
- 修改样式系统时请更新 `04-style-guide.md`
- 添加新的国际化键时请参考 `05-i18n-guide.md`
- 发现文档错误或不清晰的地方请及时修正

## 🔗 相关链接

- [项目根目录 README](../README.md) - 项目概述
- [CLAUDE.md](../CLAUDE.md) - Claude Code 入口指南
- [GitHub Copilot 指南](../.github/copilot-instructions.md) - Copilot 入口指南

## 📝 版本历史

### v2.0 (2025-01-14)
- 重构文档结构，采用数字前缀命名
- 合并测试指南，统一文档风格
- 优化文档组织，提高可读性

### v1.0 (2025-01-01)
- 初始文档结构建立
- 完成基础文档编写