// 周报生成提示词模板
// 使用 Jinja 模板引擎渲染

import 'package:jinja/jinja.dart';

import 'package:daily_satori/app/data/article/article_model.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint.dart';
import 'package:daily_satori/app/data/diary/diary_model.dart';

/// 周报提示词模板（Jinja 格式）
const String _weeklySummaryTemplate = '''
你是一个智能知识管理助手，请帮我生成{{ title }}的个人周报。

## 输出要求
1. **纯 Markdown 格式**，禁止 JSON、代码块包裹
2. 每个板块用列表形式呈现，结构清晰
3. 适当使用 emoji 增加可读性
4. 引用格式：[[type:ID:标题]]，如 [[article:123:文章标题]]

## 输出格式（三个板块，每条都是：一句话总结 + 展开说明 + 引用链接）

### 🌱 {{ periodTitle }}感悟

- **一句话总结**。展开说明具体内容... [[diary:ID:日期]] 或 [[viewpoint:ID:标题]]
- **另一个感悟总结**。具体内容...
- （2-4 条，每条独立完整）

---

### 💡 产品灵感
{% if previousAppIdeas %}
- **延续上周：xxx想法**。本周新的思考和深化...
{% endif %}
- **一句话描述产品想法**。问题是什么、解决思路是什么...
- **另一个产品方向**。具体说明...
- （1-3 条核心想法，会保存用于下周迭代）

---

### 📊 行业动态

- **一句话趋势总结**。具体说明... [[article:ID:标题]]
- **另一个行业观察**。具体内容...
- （2-4 条，附上文章引用）

---

## 格式示例

### 🌱 本周感悟

- **系统化思考比单点优化更重要**。读《原则》[[viewpoint:5:生活原则]] 时意识到，很多问题反复出现是因为没有建立系统，而只是在解决表面问题。
- **早起的关键是早睡而非闹钟**。这周尝试调整作息 [[diary:12:12月5日]]，发现强制早起只会更疲惫，真正有效的是控制晚上的时间。

---

### 💡 产品灵感

- **个人知识图谱工具**。阅读笔记之间缺乏关联，可以做一个自动提取概念并建立关联的工具。
- **专注力追踪 App**。记录每天的专注时段和干扰因素，用数据帮助改善工作习惯。

---

### 📊 行业动态

- **AI Agent 从概念走向落地**。多篇文章提到 Agent 的实际应用场景 [[article:45:AI Agent实践]]，从简单的对话扩展到任务执行。
- **本地优先架构受到更多关注**。Local-first 理念在开发者社区升温 [[article:52:Local-first软件]]，强调数据所有权和离线能力。

---

## 原始内容

{% if previousAppIdeas %}
【上周产品思考】
{{ previousAppIdeas }}

{% endif %}
{% for article in articles %}
[文章 ID:{{ article.id }}]《{{ article.title }}》
{{ article.summary }}

{% endfor %}
{% for diary in diaries %}
[日记 ID:{{ diary.id }}] {{ diary.date }}
{{ diary.summary }}

{% endfor %}
{% for viewpoint in viewpoints %}
[书摘 ID:{{ viewpoint.id }}]「{{ viewpoint.title }}」
{{ viewpoint.content }}

{% endfor %}
''';

/// Jinja 模板环境
final _env = Environment();

/// 周报输入数据
class WeeklySummaryInput {
  final List<ArticleModel> articles;
  final List<DiaryModel> diaries;
  final List<BookViewpointModel> viewpoints;
  final String? previousAppIdeas;
  final String title;
  final String periodTitle;

  WeeklySummaryInput({
    required this.articles,
    required this.diaries,
    required this.viewpoints,
    this.previousAppIdeas,
    required this.title,
    required this.periodTitle,
  });
}

/// 构建调试模式的 AI 提示词
String buildDebugSummaryPrompt(
  List<ArticleModel> articles,
  List<DiaryModel> diaries, {
  List<BookViewpointModel>? viewpoints,
  String? previousAppIdeas,
}) {
  return _buildPrompt(
    WeeklySummaryInput(
      articles: articles,
      diaries: diaries,
      viewpoints: viewpoints ?? [],
      previousAppIdeas: previousAppIdeas,
      title: '最近',
      periodTitle: '本期',
    ),
  );
}

/// 构建生产模式的 AI 提示词
String buildProductionSummaryPrompt(
  List<ArticleModel> articles,
  List<DiaryModel> diaries,
  DateTime weekStart,
  DateTime weekEnd, {
  List<BookViewpointModel>? viewpoints,
  String? previousAppIdeas,
}) {
  final dateRange = '${weekStart.month}月${weekStart.day}日 - ${weekEnd.month}月${weekEnd.day}日';

  return _buildPrompt(
    WeeklySummaryInput(
      articles: articles,
      diaries: diaries,
      viewpoints: viewpoints ?? [],
      previousAppIdeas: previousAppIdeas,
      title: '本周（$dateRange）',
      periodTitle: '本周',
    ),
  );
}

/// 内部构建提示词
String _buildPrompt(WeeklySummaryInput input) {
  final template = _env.fromString(_weeklySummaryTemplate);

  return template.render({
    'title': input.title,
    'periodTitle': input.periodTitle,
    'previousAppIdeas': input.previousAppIdeas,
    'articles': _formatArticles(input.articles),
    'diaries': _formatDiaries(input.diaries),
    'viewpoints': _formatViewpoints(input.viewpoints),
  });
}

/// 格式化文章列表为模板数据
List<Map<String, dynamic>> _formatArticles(List<ArticleModel> articles) {
  return articles.map((article) {
    final title = article.aiTitle ?? article.title ?? '无标题';
    final content = article.aiContent ?? article.content ?? '';
    final summary = content.length > 200 ? '${content.substring(0, 200)}...' : content;

    return {'id': article.id, 'title': title, 'summary': summary.isNotEmpty ? summary : '无摘要'};
  }).toList();
}

/// 格式化日记列表为模板数据
List<Map<String, dynamic>> _formatDiaries(List<DiaryModel> diaries) {
  return diaries.map((diary) {
    final date = diary.createdAt;
    final content = diary.content;
    final summary = content.length > 150 ? '${content.substring(0, 150)}...' : content;

    return {'id': diary.id, 'date': '${date.month}月${date.day}日', 'summary': summary};
  }).toList();
}

/// 格式化书籍观点列表为模板数据
List<Map<String, dynamic>> _formatViewpoints(List<BookViewpointModel> viewpoints) {
  return viewpoints.map((vp) {
    final content = vp.content;
    final summary = content.length > 150 ? '${content.substring(0, 150)}...' : content;

    return {'id': vp.id, 'title': vp.title, 'content': summary};
  }).toList();
}
