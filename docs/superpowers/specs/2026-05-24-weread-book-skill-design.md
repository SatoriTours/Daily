# 微信读书 Skill 替换读书页逻辑设计

## 背景

当前读书页添加书籍时，通过 `BookIntelligenceService` 组合豆瓣、Wikipedia、通用远程 MCP 和默认 AI：

- `searchBooks()` 先收集外部资料，再用默认 AI 生成候选书籍。
- `generateViewpoints()` 再收集书籍资料，用默认 AI 生成 10 张观点卡。
- 外部 MCP 通过工具名和描述做启发式匹配，不区分微信读书专用能力。

目标是完全替换这套外部搜书和观点提炼逻辑，读书页只使用微信读书 Skill。设置页只需要用户填写微信读书 API Key，其他端点、协议和工具调用集成在 App 内部。

## 范围

包含：

- 新增微信读书 API Key 设置入口。
- 新增微信读书专用服务，封装 Skill 调用。
- 改造读书页添加书籍流程：搜书和核心观点生成只走微信读书 Skill。
- 未配置、失败、无结果时直接提示错误，不再使用豆瓣、Wikipedia、默认 AI 或通用远程 MCP 兜底。
- 保留本地书籍展示、观点分页、删除、书内搜索等已有本地能力。

不包含：

- 不修改数据库书籍和观点表结构。
- 不要求用户手动配置 MCP 服务地址或模板。
- 不把微信读书 Skill 暴露到通用 MCP 服务列表中。

## 架构

新增 `WeReadSkillService`，职责是：

- 从 `SettingRepository` 读取 `weread_api_key`。
- 使用内置微信读书 Skill 配置和 MCP/Skill 调用协议。实现时先解析 `Tencent/WeChatReading` Skill 元数据，确认端点、认证方式、工具名和入参，再把这些配置封装在服务层，不暴露给用户编辑。
- 调用书籍搜索能力，转换为现有 `BookSearchResult`。
- 调用书籍详情、目录、书评等能力，整理为现有 `BookViewpointDraft`。
- 在未配置或调用失败时抛出明确业务错误，由 ViewModel 转换为 UI 文案。

`BookIntelligenceService` 保留对读书页的接口形状，但内部改为委托 `WeReadSkillService`：

- `searchBooks(query)` 只调用微信读书搜索。
- `generateViewpoints(book)` 只调用微信读书资料生成观点。
- 移除该路径上的豆瓣、Wikipedia、默认 AI 和通用远程 MCP 兜底。

这样可以最小化 UI 层改动，同时让读书页处理逻辑完成替换。

## 设置

在设置页新增“微信读书”入口，放在“AI 与服务”区域。页面只展示：

- API Key 输入框。
- 保存按钮。
- 清空按钮或空值保存能力。
- 简短说明：用于连接微信读书 Skill，读书页搜书和观点提炼依赖此 Key。

API Key 存储在 `setting` 表，新增常量：

- `SettingKeys.weReadApiKey = "weread_api_key"`

当前 `setting` 表是键值存储，新增 key 不需要数据库迁移。

## 数据流

搜书流程：

1. 用户在读书页输入关键词。
2. `BookSearchViewModel.search()` 调用 `BookIntelligenceService.searchBooks(query)`。
3. `BookIntelligenceService` 调用 `WeReadSkillService.searchBooks(query)`。
4. `WeReadSkillService` 使用内置微信读书 Skill 配置和 API Key 调用书籍搜索。
5. 返回结果映射为 `BookSearchResult`，UI 继续使用现有候选列表展示。

添加并分析流程：

1. 用户选择候选书，点击“添加并分析”。
2. 先插入 `book`，保持当前用户可见行为。
3. 调用 `WeReadSkillService.generateViewpoints(book)` 获取微信读书资料并生成 10 张观点卡。
4. 观点卡写入 `book_viewpoint`。
5. 成功后跳回阅读页展示本地观点。

## 错误处理

错误不再触发旧兜底。

- 未配置 API Key：提示“请先在设置中配置微信读书 API Key”。
- 搜索无结果：提示“微信读书未找到相关书籍”。
- Skill 调用失败：提示“微信读书服务调用失败，请稍后重试”。
- 观点生成失败：保留已添加书籍，提示“分析失败，可重新生成观点”。

ViewModel 继续维护加载态、分析态和错误态，不新增复杂 UI 状态。

## 测试

单元测试覆盖：

- 未配置 API Key 时，搜书和观点生成返回明确错误。
- 微信读书搜索响应可解析为 `BookSearchResult`。
- 微信读书详情/书评/目录响应可整理成 10 个 `BookViewpointDraft`。
- `BookIntelligenceService` 不再调用旧的 `BookSearchService`、默认 AI 或通用远程 MCP 兜底。
- 设置页 ViewModel 可读取、保存、清空 API Key。

构建验证：

- 修改后执行 `./gradlew :app:compileDebugKotlin`。
- 若需要部署验证，再执行 `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` 并启动 App。

## 风险

- 微信读书 Skill 的实际工具名和返回结构需要在实现时通过 `Tencent/WeChatReading` Skill 元数据确认，服务层应集中处理解析差异。
- 如果 Skill 端点不可用，读书页将无法添加新书或生成观点，这是符合“不兜底”的产品约束。
- API Key 属于敏感信息，不能写日志，UI 也不应在列表页明文展示。
