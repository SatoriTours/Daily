<div align="center">

# 📚 Daily Satori

**本地优先的智能知识管理工具**

快速收集 · AI 整理 · 智能搜索 · 知识沉淀

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Flutter](https://img.shields.io/badge/Flutter-%3E%3D3.0-blue.svg)](https://flutter.dev/)
[![Version](https://img.shields.io/badge/Version-3.6.63-brightgreen.svg)](https://github.com/SatoriTours/Daily/releases)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/SatoriTours/Daily/pulls)

[功能特性](#-功能特性) · [快速开始](#-快速开始) · [更新日志](#-更新日志) · [参与贡献](#-参与贡献)

</div>

---

## 🎯 为什么选择 Daily Satori？

- 🔒 **隐私第一** - 所有数据本地存储，零云依赖
- 🤖 **AI 增强** - 智能摘要、搜索、总结，让信息流动起来
- 🚀 **现代体验** - Flutter 构建，流畅优雅的原生体验
- 🧩 **高度可扩展** - 插件机制、自定义提示词、多语言支持

## ✨ 功能特性

### 📝 内容收集与管理
- 🌐 **一键收藏** - 智能解析网页，自动提取标题、正文、图片
- 🔍 **全文搜索** - 毫秒级检索所有文章、日记、书籍
- 🏷️ **灵活组织** - 标签、收藏、分类，随心管理
- 🛡️ **广告过滤** - 内置 ADBlock，纯净阅读体验
- 💾 **离线可用** - 全文与图片本地缓存，随时随地阅读

### 🤖 AI 智能助手 <sup>NEW v3.6.60</sup>
- 💬 **智能对话** - 与你的知识库对话，快速找到答案
- 🔎 **语义搜索** - 理解问题意图，智能检索相关内容
- 📊 **自动总结** - AI 分析搜索结果，生成结构化答案
- ⚡ **关键词扩展** - 智能扩展同义词、相关词，提升搜索召回率（15-20个关键词）
- 🎯 **精准匹配** - 多维度搜索（标题、内容、标签、日期）
- 📋 **结果展示** - 可折叠的搜索结果卡片，清晰呈现来源
- ✨ **Markdown 渲染** - 优化的对话展示，支持加粗、列表、代码块

### 📚 书籍管理
- 🔍 **智能搜索** - 中文查询自动转拼音，调用 OpenLibrary API
- 🖼️ **精美封面** - 自动获取高清封面图片
- 🌍 **AI 翻译** - 英文书籍信息智能翻译为中文
- 🎯 **相关性过滤** - AI 评分过滤不相关结果（处理拼音歧义）
- 💡 **核心观点** - AI 提取书籍核心思想与要点

### 🎨 阅读体验
- 📖 **Markdown 渲染** - 优化的排版与样式，舒适阅读
- 🎯 **重点提取** - AI 自动标注文章要点
- 📄 **格式转换** - HTML 一键转 Markdown
- 🌓 **深色模式** - 保护眼睛，随时切换

### 🌐 多端访问
- 🖥️ **Web 服务** - 局域网浏览器访问所有内容
- 🔐 **安全加密** - 自定义端口和访问密码
- 📱 **响应式设计** - 适配各种屏幕尺寸

### 🔧 扩展能力
- 🧩 **插件系统** - 自定义提示词模板，扩展 AI 能力
- 🌍 **多语言支持** - 中英文界面，可轻松添加新语言
- 📦 **数据备份** - 导出/导入，数据安全无忧
- 🔄 **自动更新** - 应用内检测并升级新版本

## 📸 界面预览

<table>
  <tr>
    <td align="center"><b>文章列表</b></td>
    <td align="center"><b>AI 解读</b></td>
    <td align="center"><b>AI 助手</b></td>
    <td align="center"><b>日记</b></td>
  </tr>
  <tr>
    <td><img src="docs/images/文章列表.jpg" width="200"/></td>
    <td><img src="docs/images/AI解读.jpg" width="200"/></td>
    <td><img src="docs/images/markdown.jpg" width="200"/></td>
    <td><img src="docs/images/日记.jpg" width="200"/></td>
  </tr>
</table>

## 🚀 快速开始

### 环境要求
```
Flutter SDK >= 3.0
Dart SDK >= 2.17
```

### 安装运行

```bash
# 克隆仓库
git clone https://github.com/SatoriTours/Daily.git
cd Daily

# 安装依赖
flutter pub get

# 运行应用
flutter run
```

### 初始配置
1. 进入"设置"页面
2. （可选）配置 AI Key 和 API Base URL
3. 配置 Web 服务（端口和密码）
4. 开始使用！

> 💡 提示：未配置 AI 时仍可作为纯内容管理工具使用

## 🏗️ 技术栈

```
框架：Flutter + GetX
数据库：ObjectBox + SQLite
网络：Dio + 自定义解析流水线
AI：openai_dart
书籍搜索：OpenLibrary API + pinyin
Web 服务：shelf + WebSocket
其他：ADBlock 过滤、插件化架构
```

## 📋 更新日志

### v3.6.63 (2025-11-30)

**🚀 性能优化**
- 主页面延迟加载，显著提升应用启动速度
- 应用更新支持下载进度条显示

**🤖 AI 搜索增强**
- 优化 AI 文章搜索逻辑，结果更精准
- 改进搜索结果处理和显示效果
- 优化 AI Chat 代码架构，更简洁高效

**🎨 界面优化**
- 优化 Markdown 渲染样式
- 改进 AI 对话界面显示效果
- 优化文章处理逻辑

**🐛 问题修复**
- 修复剪切板相关问题
- 修复 AI 搜索的已知问题

### v3.6.60 (2025-11-24) 🎉

**✨ AI 智能助手重磅上线**
- 全新 AI 对话界面，与知识库智能交互
- 智能意图识别（文章/日记/书籍/综合搜索）
- AI 自动总结搜索结果，生成结构化答案
- 可折叠的搜索结果卡片，清晰展示来源

**🔧 优化改进**
- 重构 AI Agent 服务，代码更简洁高效
- 提示词统一管理，便于维护和调整
- 优化日志输出，更清晰简洁
- 紧凑的欢迎消息和 UI 布局
- 修复设置页面导航错误

### v3.6.53 (2025-11-10)

**🎉 新增功能**
- 智能书籍搜索：集成 OpenLibrary API，支持中文
- 多语言国际化框架
- 书籍封面自动获取
- AI 相关性过滤

**🔧 优化改进**
- 重构书籍搜索架构
- 优化仓储层、配置层
- 修复书籍添加问题

[查看完整更新日志](https://github.com/SatoriTours/Daily/releases)

## 🤝 参与贡献

欢迎各种形式的贡献！

- 🐛 报告 Bug
- 💡 提出新功能建议
- 🔧 提交代码改进
- 📝 完善文档

提交 PR 前请：
1. 阅读 [编码规范](./docs/STYLE_GUIDE.md)
2. 运行 `flutter analyze` 检查代码
3. 运行 `flutter format .` 格式化代码

## ❓ 常见问题

**数据会上传云端吗？**
不会。所有数据存储在本地，仅在配置 AI 时调用外部 API。

**必须配置 AI 吗？**
不是必须的。未配置时可作为纯内容收集工具使用。

**支持哪些 AI 模型？**
支持所有兼容 OpenAI API 的模型和服务。

**如何添加新语言？**
修改 `assets/i18n/` 目录下的配置文件即可。

## 🔒 隐私声明

- ✅ 本地优先 - 数据存储在设备本地
- ✅ 无云依赖 - 不依赖任何云服务
- ✅ 可选 AI - AI 功能仅在配置后启用
- ✅ 零追踪 - 不收集任何用户行为数据

## 📄 开源协议

[MIT License](LICENSE)

## 🔗 相关链接

- 📦 [发行版本](https://github.com/SatoriTours/Daily/releases)
- 🐛 [问题反馈](https://github.com/SatoriTours/Daily/issues)
- 💬 [讨论交流](https://github.com/SatoriTours/Daily/discussions)
- 🧩 [插件示例](https://github.com/SatoriTours/plugin)

---

<div align="center">

**让信息收集更轻松，知识沉淀更高效**

Made with ❤️ by [SatoriTours](https://github.com/SatoriTours)

⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！

</div>
