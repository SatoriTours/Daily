// 周报生成提示词模板
// 使用 Jinja 模板引擎渲染

import 'package:jinja/jinja.dart';

import 'package:daily_satori/app/data/article/article_model.dart';
import 'package:daily_satori/app/data/diary/diary_model.dart';

/// 周报提示词模板（Jinja 格式）
const String _weeklySummaryTemplate = '''
你是一个知识管理助手，请帮我总结{{ title }}的阅读和思考内容。

## 输出要求
1. **纯 Markdown 格式**，禁止 JSON、代码块包裹
2. **总字数 150-250 字**，精炼简洁
3. 适当使用 emoji 增加可读性

## 输出格式

请将文章和日记内容融合，写成 2-3 段连贯的总结：

**第一段：{{ periodTitle }}主题**
概括{{ periodTitle }}主要关注了什么领域/话题，有什么核心收获或发现。提及 1-2 篇印象深刻的文章 [[article:ID:标题]]。

**第二段：思考与感悟**
结合日记内容，描述{{ periodTitle }}的状态、情绪或重要事件。如有日记可引用 [[diary:ID:日期]]。

**第三段：一句话收尾** 💡
用一句话给出{{ periodTitle }}的核心感悟或下周期待。

## 注意事项
- 文章引用：[[article:ID:标题]]
- 日记引用：[[diary:ID:日期]]
- 写成流畅的段落，不要用列表罗列
- 内容要具体，避免空洞的套话
{% if not hasDiaries %}- 没有日记数据时，第二段可侧重于阅读感受{% endif %}

---

以下是原始内容：

{% for article in articles %}
[文章 ID:{{ article.id }}]《{{ article.title }}》
{{ article.summary }}

{% endfor %}
{% for diary in diaries %}
[日记 ID:{{ diary.id }}] {{ diary.date }}
{{ diary.summary }}

{% endfor %}
''';

/// Jinja 模板环境
final _env = Environment();

/// 构建调试模式的 AI 提示词
String buildDebugSummaryPrompt(List<ArticleModel> articles, List<DiaryModel> diaries) {
  final template = _env.fromString(_weeklySummaryTemplate);

  return template.render({
    'title': '最近',
    'periodTitle': '本期',
    'hasDiaries': diaries.isNotEmpty,
    'articles': _formatArticles(articles),
    'diaries': _formatDiaries(diaries),
  });
}

/// 构建生产模式的 AI 提示词
String buildProductionSummaryPrompt(
  List<ArticleModel> articles,
  List<DiaryModel> diaries,
  DateTime weekStart,
  DateTime weekEnd,
) {
  final template = _env.fromString(_weeklySummaryTemplate);
  final dateRange = '${weekStart.month}月${weekStart.day}日 - ${weekEnd.month}月${weekEnd.day}日';

  return template.render({
    'title': '本周（$dateRange）',
    'periodTitle': '本周',
    'hasDiaries': diaries.isNotEmpty,
    'articles': _formatArticles(articles),
    'diaries': _formatDiaries(diaries),
  });
}

/// 格式化文章列表为模板数据
List<Map<String, dynamic>> _formatArticles(List<ArticleModel> articles) {
  return articles.map((article) {
    final title = article.aiTitle ?? article.title ?? '无标题';
    final content = article.aiContent ?? article.content ?? '';
    final summary = content.length > 150 ? '${content.substring(0, 150)}...' : content;

    return {'id': article.id, 'title': title, 'summary': summary.isNotEmpty ? summary : '无摘要'};
  }).toList();
}

/// 格式化日记列表为模板数据
List<Map<String, dynamic>> _formatDiaries(List<DiaryModel> diaries) {
  return diaries.map((diary) {
    final date = diary.createdAt;
    final content = diary.content;
    final summary = content.length > 80 ? '${content.substring(0, 80)}...' : content;

    return {'id': diary.id, 'date': '${date.month}月${date.day}日', 'summary': summary};
  }).toList();
}
