import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// AI处理器
class AiProcessor {
  /// 处理文章AI任务
  Future<void> process(ArticleModel article) async {
    final id = article.id;
    logger.i('[WebContent] AI处理: #$id');

    try {
      // 并行处理所有AI任务
      await Future.wait([
        _processTitle(article),
        _processSummary(article),
        _processMarkdown(article),
      ]);
      logger.i('[WebContent] AI完成: #$id');
    } catch (e) {
      logger.e('[WebContent] AI失败: #$id, $e');
      throw Exception('AI处理失败: $e');
    }
  }

  Future<void> _processTitle(ArticleModel article) async {
    final title = article.title ?? '';
    if (title.isEmpty) return;

    var processed = title;
    // 非中文则翻译
    if (!RegExp(r'[\u4e00-\u9fff]').hasMatch(title)) {
      processed = await AiService.i.translate(title.trim());
    }
    // 标题太长则概括
    if (processed.length >= 50) {
      processed = await AiService.i.summarizeOneLine(processed);
    }
    article.aiTitle = processed;
    ArticleRepository.i.updateModel(article);
  }

  Future<void> _processSummary(ArticleModel article) async {
    final content = article.content ?? '';
    if (content.isEmpty) return;

    final (summary, tags) = await AiService.i.summarize(content.trim());
    if (summary.isEmpty) return;

    article.aiContent = summary;
    for (final tag in tags) {
      await TagRepository.i.addTagToArticle(article, tag);
    }
    ArticleRepository.i.updateModel(article);
  }

  Future<void> _processMarkdown(ArticleModel article) async {
    final html = article.htmlContent ?? '';
    if (html.isEmpty) return;
    article.aiMarkdownContent = await AiService.i.htmlToMarkdown(html);
    ArticleRepository.i.updateModel(article);
  }
}
