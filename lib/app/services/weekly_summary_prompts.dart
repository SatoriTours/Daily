// 周报生成提示词模板
// 使用 Jinja 模板引擎渲染
// 模板内容存储在 assets/configs/ai_prompts.yaml 中

import 'package:jinja/jinja.dart';

import 'package:daily_satori/app/data/article/article_model.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint.dart';
import 'package:daily_satori/app/data/diary/diary_model.dart';
import 'package:daily_satori/app/services/plugin_service.dart';

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
  final dateRange =
      '${weekStart.month}月${weekStart.day}日 - ${weekEnd.month}月${weekEnd.day}日';

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
  final template = _env.fromString(PluginService.i.weeklySummaryTemplate);

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
    final summary = content.length > 200
        ? '${content.substring(0, 200)}...'
        : content;

    return {
      'id': article.id,
      'title': title,
      'summary': summary.isNotEmpty ? summary : '无摘要',
    };
  }).toList();
}

/// 格式化日记列表为模板数据
List<Map<String, dynamic>> _formatDiaries(List<DiaryModel> diaries) {
  return diaries.map((diary) {
    final date = diary.createdAt;
    final content = diary.content;
    final summary = content.length > 150
        ? '${content.substring(0, 150)}...'
        : content;

    return {
      'id': diary.id,
      'date': '${date.month}月${date.day}日',
      'summary': summary,
    };
  }).toList();
}

/// 格式化书籍观点列表为模板数据
List<Map<String, dynamic>> _formatViewpoints(
  List<BookViewpointModel> viewpoints,
) {
  return viewpoints.map((vp) {
    final content = vp.content;
    final summary = content.length > 150
        ? '${content.substring(0, 150)}...'
        : content;

    return {'id': vp.id, 'title': vp.title, 'content': summary};
  }).toList();
}
