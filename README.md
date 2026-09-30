<div align="center">

# Daily Satori

**从每天看到的信息里，找到值得行动的想法。**

看懂新闻 · 留下思考 · 发现机会

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/SatoriTours/Daily?label=Release)](https://github.com/SatoriTours/Daily/releases)

[下载 Android 版](https://github.com/SatoriTours/Daily/releases) · [问题反馈](https://github.com/SatoriTours/Daily/issues) · [讨论交流](https://github.com/SatoriTours/Daily/discussions)

</div>

每天读了很多，真正留下了什么？

Daily Satori 把新闻、阅读和日常思考连起来：用每日重点了解发生了什么，用日记和读书留下自己的理解，再从新闻中发现与你的关注方向有关的产品机会，把值得尝试的想法带进待办。

## 让每天的信息，更有用一点

**先看懂今天，再决定深入哪一条。**

打开新闻页，先读一段「今日要闻」，了解重要动态；感兴趣再点进去读具体新闻和来源。把握重点，也保留深入阅读的入口。

**让机会跟着你的关注持续积累。**

「产品机会」结合新闻和你的关注方向，给出值得验证的切入点与新闻依据。机会跨天积累、持续排序，首页展示前 5 项；收藏的优先展示，加入待办的转入后续行动。

**让自己的想法，有来处，也能找回来。**

日记留下经历和灵感，读书留下观点与反思。AI 助手可以结合本地文章、日记和读书观点回答问题，并附上引用，让你回到当时的记录继续思考。

> 试着问：「我最近一直在关注什么？」或「以前收藏过哪些与这个想法有关的内容？」

## 从看见，到想清楚，再到行动

四个入口，各有一个清楚的用途：

| 入口 | 你可以从这里开始 |
|------|------------------|
| **新闻** | 看每日重点，读具体新闻，发现值得验证的产品机会。 |
| **日记** | 记下今天的经历和想法，回看一个月的积累。 |
| **读书** | 阅读核心观点，用「想一想」留下自己的理解。 |
| **我的** | 回看思想和文章收藏，在待办里跟进行动；右上角齿轮进入设置。 |

比如，新闻里的一项变化让你想到一个产品点子：先收藏机会，写下自己的判断，再加入待办去验证。日后需要回顾时，可以通过 AI 助手找到相关记录和依据。

## 看看实际界面

| 每日重点：快速了解今天 | 产品机会：找到下一步 | 读书：留下自己的理解 |
|:---:|:---:|:---:|
| [![每日重点](docs/images/home.png)](docs/images/home.png) | [![产品机会](docs/images/opportunities.png)](docs/images/opportunities.png) | [![读书观点](docs/images/books.png)](docs/images/books.png) |

截图使用演示内容，点击可查看大图；界面可能随版本更新调整。

## 开始使用

1. 从 [Releases](https://github.com/SatoriTours/Daily/releases) 下载 APK，安装到 **Android 8.0 及以上**设备。
2. 在「我的 → 设置」配置 AI 服务和新闻来源，也可以通过系统分享保存文章。
3. 打开新闻页看每日重点，再写一条日记，或从一本书开始。逐步积累后，回看自己的思想与关注方向，把值得推进的发现加入待办。

**内容默认保存在手机里，AI 服务由你选择。** 使用 AI 整理或对话时，相关内容会发送到你配置的模型服务；外部来源需要主动配置。你也可以在设置中备份与恢复数据。

## 开源与贡献

基于 **Kotlin Multiplatform + Jetpack Compose** 构建，当前提供 Android 应用。

[功能说明](docs/03-app-features.md) · [开发文档](docs/README.md) · [测试与构建](docs/02-testing.md) · [新闻源接入](docs/08-remote-news-api.md) · [项目规则](AGENTS.md)

采用 [MIT License](LICENSE)。感谢 OpenAI Codex 在代码编写、测试与维护中提供的帮助。

Made by [SatoriTours](https://github.com/SatoriTours)
