# 远程新闻接口标准

本文档定义 Daily Satori 远程新闻源需要支持的接口格式。新增远程新闻服务时，应优先兼容本文档中的 `top_articles_today` 列表接口。

## 核心约定

远程新闻服务只需要提供一个列表接口：

```http
GET /api/v1/external/top_articles_today?page=1&per_page=50&limit=50
Authorization: Bearer <TOKEN>
X-Api-Token: <TOKEN>
```

APP 从该接口获取新闻列表。每篇新闻应直接包含详情页需要的完整内容，点开新闻详情时直接显示列表接口返回的文章内容，不依赖单独的文章详情接口。

## 请求示例

```http
GET https://your-domain.com/api/v1/external/top_articles_today?page=1&per_page=50&limit=50
Authorization: Bearer YOUR_TOKEN
X-Api-Token: YOUR_TOKEN
```

## Query 参数

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `page` | number | 否 | 页码，默认 `1` |
| `per_page` | number | 否 | 每页数量，默认 `50` |
| `limit` | number | 否 | 返回数量上限，建议与 `per_page` 一致 |

## 鉴权

服务端建议同时兼容以下两种 Token 传递方式：

```http
Authorization: Bearer YOUR_TOKEN
X-Api-Token: YOUR_TOKEN
```

## 推荐响应格式

```json
{
  "articles": [
    {
      "id": 123,
      "title": "OpenAI 发布新的编程模型",
      "url": "https://example.com/original-article",
      "summary": "这是一段中文摘要，概括文章核心内容。",
      "viewpoints": [
        "观点一：模型提升了代码生成稳定性",
        "观点二：支持更长上下文",
        "观点三：面向开发者和企业场景优化"
      ],
      "content": "# OpenAI 发布新的编程模型\n\n这里是完整文章正文，建议使用 Markdown。\n\n## 背景\n\n正文内容……",
      "cover_url": "https://example.com/cover.jpg",
      "domain": "example.com",
      "feed_id": 12,
      "feed_name": "Example Feed",
      "source_type": "rss",
      "importance_score": 0.87,
      "status": "completed",
      "published_at": "2026-05-30T07:30:00Z",
      "created_at": "2026-05-30T08:00:00Z",
      "processed_at": "2026-05-30T08:10:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "per_page": 50,
    "total": 120,
    "total_pages": 3,
    "next": 2
  }
}
```

## 最小必需字段

每篇文章至少需要返回：

```json
{
  "id": 123,
  "title": "文章标题",
  "url": "https://example.com/original-article",
  "summary": "文章摘要",
  "content": "完整正文 Markdown"
}
```

## 推荐完整字段

```json
{
  "id": 123,
  "title": "文章标题",
  "url": "https://example.com/original-article",
  "summary": "文章摘要",
  "viewpoints": ["关键观点 1", "关键观点 2"],
  "content": "完整正文 Markdown",
  "cover_url": "https://example.com/cover.jpg",
  "domain": "example.com",
  "feed_id": 12,
  "feed_name": "信息源名称",
  "source_type": "rss",
  "importance_score": 0.87,
  "status": "completed",
  "published_at": "2026-05-30T07:30:00Z",
  "created_at": "2026-05-30T08:00:00Z",
  "processed_at": "2026-05-30T08:10:00Z"
}
```

## 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | number | 是 | 文章唯一 ID，必须稳定 |
| `title` | string | 是 | 文章标题 |
| `url` | string | 是 | 原始文章 URL；用于打开浏览器、收藏去重、英文文章本地 AI 重处理 |
| `summary` | string | 是 | 摘要页显示内容，建议中文 |
| `content` | string | 是 | 详情页正文内容，建议 Markdown |
| `viewpoints` | string[] | 否 | 关键观点列表，推荐中文 |
| `cover_url` | string | 否 | 封面图 URL |
| `domain` | string | 否 | 来源域名 |
| `feed_id` | number | 否 | 信息源 ID |
| `feed_name` | string | 否 | 信息源名称 |
| `source_type` | string | 否 | 来源类型，例如 `rss`、`manual` |
| `importance_score` | number | 否 | 重要性分数 |
| `status` | string | 否 | 文章状态，建议 `completed` |
| `published_at` | string | 否 | 原文发布时间，ISO-8601 格式；优先用于文章显示和本地保存发布时间 |
| `created_at` | string | 否 | 创建时间，ISO-8601 格式 |
| `processed_at` | string | 否 | 处理完成时间，ISO-8601 格式 |

## 服务端逻辑要求

远程新闻服务不只是返回 JSON 字段，还需要保证列表中的每篇文章已经具备可阅读的详情内容。APP 不再依赖单独的详情接口，因此服务端应在返回列表前完成抓取、清洗、摘要和正文准备。

### 文章收集范围

`top_articles_today` 应返回“当前自然日或服务端定义的今日窗口”内的新闻。建议服务端使用固定时区，例如 `Asia/Shanghai`，并在内部保持一致。

如果服务端有多个来源，应合并 RSS、网页、手动添加等来源后统一排序返回。不同来源的文章需要使用同一套字段格式。

### 文章入选条件

列表中建议只返回可展示的文章：

1. 必须有稳定 `id`。
2. 必须有 `title` 或可生成的标题。
3. 必须有 `url`，除非文章来自内部手动录入且没有外部链接。
4. 必须有 `summary` 或 `viewpoints`。
5. 必须有 `content`，且 `content` 应是可阅读正文而不是空字符串、抓取日志、错误堆栈或 HTML 噪音。

如果文章仍在抓取或 AI 处理中，建议暂不返回，或返回 `status: "processing"` 但仍提供当前可用的 `summary` 和 `content`。不建议返回只有标题、没有正文的文章。

### 正文处理要求

服务端应在 `content` 中返回完整正文，推荐 Markdown。正文处理建议满足以下要求：

1. 去除导航、广告、版权栏、推荐阅读、脚本、样式等非正文噪音。
2. 保留正文的标题层级、段落、列表、代码块、表格、引用、链接和图片。
3. 图片使用公开可访问的 URL，不要使用本地文件路径或需要登录的临时地址。
4. 不要只返回摘要；详情页会直接显示 `content`。
5. 如果原文是英文，可以返回英文正文；APP 收藏到本地时会触发本地 AI 中文重处理。

### 摘要和观点要求

`summary` 用于详情页摘要 Tab 和列表卡片简介，建议是中文自然语言摘要。`viewpoints` 用于展示关键观点，建议返回 2 到 5 条。

摘要和观点应遵守以下要求：

1. 只基于正文内容，不编造事实。
2. 不输出服务端处理说明，例如“以下是摘要”。
3. 不包含调试信息、Prompt、模型返回原始 JSON 或异常信息。
4. 中文新闻源优先返回中文摘要；英文源如果服务端已具备翻译能力，也建议返回中文摘要。

### 去重要求

服务端应尽量避免同一篇文章重复出现在列表中。推荐按以下优先级去重：

1. 规范化后的原文 `url`。
2. 来源内部文章 ID。
3. 标题加来源域名。

如果同一篇文章出现在多个 feed 中，建议只返回一条，`feed_name` 可以使用最主要的来源名称。

### 排序要求

列表应优先返回最重要或最新的文章。推荐排序规则：

1. `importance_score` 高的优先。
2. `published_at`、`processed_at` 或 `created_at` 新的优先。
3. 同分时使用稳定的 `id` 倒序。

排序必须稳定。同样的请求参数在短时间内不应频繁改变顺序，否则 APP 刷新和分页体验会不稳定。

### 分页要求

`page` 从 `1` 开始。服务端应根据 `page` 和 `per_page` 返回对应页的数据，并设置 `pagination.next`。

分页建议：

1. 有下一页时，`next` 返回下一页页码。
2. 没有下一页时，`next` 返回 `null`。
3. 不支持分页时，也应返回第一页数据，并设置 `next: null`。
4. `total` 和 `total_pages` 可以为估算值，但不应与 `next` 明显矛盾。

### 缓存和性能要求

因为列表接口需要返回完整正文，服务端应避免每次请求都实时抓取和 AI 处理所有文章。推荐服务端提前异步抓取和处理，然后接口只读取已处理结果。

建议：

1. 对 feed 抓取和正文解析做后台任务。
2. 对摘要、观点和 Markdown 正文做持久化缓存。
3. 接口响应时间建议控制在 3 秒以内。
4. 如果后台任务失败，应返回上一份可用结果，而不是让接口整体 500。

### 失败隔离要求

单篇文章处理失败不应导致整个列表接口失败。服务端应跳过失败文章，或返回其他可用文章。

只有以下情况才建议返回非 `200`：

1. Token 缺失或无效。
2. 请求参数非法。
3. 服务整体不可用，例如数据库连接失败。
4. 触发限流或权限限制。

### 安全要求

服务端返回内容前应做基本清洗：

1. 不返回 API Token、Cookie、内部路径、堆栈、环境变量等敏感信息。
2. 不返回未转义的脚本内容。
3. 图片和链接 URL 应使用 `http` 或 `https`。
4. 不要返回 `file://`、`javascript:` 等危险链接。

### 稳定性要求

`id` 和 `url` 必须尽量稳定。APP 会用它们做列表 key、详情打开、收藏去重和本地文章关联。

不推荐：

1. 每次请求都重新生成随机 `id`。
2. `url` 带一次性 token 或短期签名参数。
3. 同一篇文章今天返回一个 `id`，明天返回另一个 `id`。

## 分页字段说明

```json
{
  "pagination": {
    "page": 1,
    "per_page": 50,
    "total": 120,
    "total_pages": 3,
    "next": 2
  }
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `page` | number | 否 | 当前页码 |
| `per_page` | number | 否 | 每页数量 |
| `total` | number | 否 | 总文章数 |
| `total_pages` | number | 否 | 总页数 |
| `next` | number/null | 否 | 下一页页码；没有下一页时返回 `null` |

## 兼容格式

如果服务端暂时不方便返回 `{ articles, pagination }` 对象，也可以直接返回数组：

```json
[
  {
    "id": 123,
    "title": "文章标题",
    "url": "https://example.com/original-article",
    "summary": "文章摘要",
    "content": "完整正文 Markdown"
  }
]
```

但推荐使用标准对象格式：

```json
{
  "articles": [],
  "pagination": {}
}
```

## 空列表格式

当天没有新闻时，应返回 `200 OK` 和空数组，不要用 `404` 表示今天没有新闻。

```json
{
  "articles": [],
  "pagination": {
    "page": 1,
    "per_page": 50,
    "total": 0,
    "total_pages": 0,
    "next": null
  }
}
```

## 错误响应格式

推荐错误格式：

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Invalid token"
  }
}
```

也兼容简单格式：

```json
{
  "error": "bad_request",
  "message": "page must be greater than 0"
}
```

APP 会优先读取 `error.message`，其次读取顶层 `message`，最后读取字符串形式的 `error`。

## HTTP 状态码约定

| 状态码 | 含义 | 建议响应 |
|--------|------|----------|
| `200` | 请求成功 | 返回文章列表，空列表也用 `200` |
| `400` | 参数错误 | 返回错误 JSON，例如 `page` 或 `per_page` 非法 |
| `401` | Token 缺失或无效 | 返回 `unauthorized` 错误 JSON |
| `403` | Token 有效但无权限 | 返回 `forbidden` 错误 JSON |
| `429` | 请求过于频繁 | 返回 `rate_limited` 错误 JSON，可带 `Retry-After` 响应头 |
| `500` | 服务端异常 | 返回 `internal_error` 错误 JSON |
| `503` | 服务暂时不可用 | 返回 `service_unavailable` 错误 JSON |

APP 当前主要按 HTTP 状态码区分客户端错误、服务端错误和网络错误；如果响应体中包含上述错误 JSON，会优先展示其中的 `message`。

## 字段兼容别名

列表容器兼容以下形式：

| 标准字段 | 兼容字段 |
|----------|----------|
| `articles` | `data`、`data.articles`、`top_articles`、`items`、`results`、直接数组 |

文章字段兼容以下别名：

| 标准字段 | 兼容字段 |
|----------|----------|
| `id` | `article_id` |
| `title` | `headline` |
| `url` | `link` |
| `summary` | `description` |
| `source_type` | `sourceType` |
| `feed_id` | `feedId` |
| `feed_name` | `feedName` |
| `importance_score` | `importanceScore` |
| `cover_url` | `coverUrl` |
| `published_at` | `publishedAt` |
| `created_at` | `createdAt` |
| `processed_at` | `processedAt` |

`viewpoints` 推荐返回字符串数组，也兼容换行分隔的字符串和 `null`。

## 字段缺失时的 APP 行为

| 字段 | 缺失或为空时的行为 |
|------|--------------------|
| `id` | 该文章会被忽略，无法进入列表 |
| `title` | 卡片和详情标题可能为空，不推荐 |
| `url` | 仍可显示，但无法在浏览器打开；收藏去重和英文文章本地 AI 重处理会受影响 |
| `summary` | 摘要页会主要依赖 `viewpoints`；两者都空时摘要内容不足 |
| `content` | 详情原文页会显示“暂无原文内容”，不推荐 |
| `viewpoints` | 不显示关键观点，仅显示摘要 |
| `cover_url` | 不显示封面图 |
| `published_at` / `created_at` / `processed_at` | 时间信息可能缺失，保存到本地时发布时间可能丢失 |

## 内容、时间与图片规范

`content` 推荐返回 Markdown，不推荐返回 HTML。正文图片使用标准 Markdown 图片语法：

```markdown
![图片描述](https://example.com/image.jpg)
```

链接使用标准 Markdown 链接语法：

```markdown
[链接文本](https://example.com)
```

不要返回 base64 图片，也不要返回需要登录后才能访问的图片 URL。

时间字段推荐 ISO-8601 格式：

```text
2026-05-30T08:10:00Z
2026-05-30T08:10:00+08:00
```

不推荐返回以下格式：

```text
2026/05/30 08:10
May 30, 2026
```

因为 APP 可能无法解析这些时间格式。

时间语义：

1. `published_at` 表示原文实际发表时间，优先用于展示和保存到本地文章的 `pub_date`。
2. `created_at` 表示远程新闻服务收录或创建该文章记录的时间。
3. `processed_at` 表示远程新闻服务完成正文解析、摘要或 AI 处理的时间。
4. 如果三个字段同时存在，APP 按 `published_at`、`processed_at`、`created_at` 的优先级选择本地文章发布时间。

## 大小建议

由于详情页直接使用列表接口返回的 `content`，列表响应不宜过大：

1. 单篇 `content` 建议控制在 200KB 以内。
2. 单次响应建议控制在 5MB 以内。
3. 如果文章正文特别长，服务端可以保留主要正文，但不要只返回摘要。

## 注意事项

1. `content` 应包含完整文章正文，APP 新闻详情页会直接显示它。
2. `content` 建议使用 Markdown。
3. `summary` 和 `viewpoints` 建议返回中文。
4. 如果 `content` 是英文，APP 收藏到本地新闻时会触发本地 AI 重新生成中文内容。
5. `url` 必须尽量稳定，否则本地收藏去重会失效。
6. `id` 必须稳定，否则列表刷新和详情打开可能出现错乱。
7. `viewpoints` 推荐返回字符串数组，也兼容换行字符串。
8. 未列出的额外字段 APP 会忽略。

## 不再要求的接口

以后不建议依赖单独的文章详情接口：

```http
GET /api/v1/external/articles/{id}
```

新的 APP 逻辑应以 `top_articles_today` 返回的每篇文章完整内容为准。
