import 'dart:async';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

/// AI 文章处理常量
class AiArticleConstants {
  AiArticleConstants._();
  static const int minHtmlLength = 100; // HTML 内容最小长度
  static const int minTextLength = 50; // 文本内容最小长度
  static const int longTitleThreshold = 50; // 标题过长阈值
}

/// 负责文章的 AI 相关处理（标题翻译/概括、摘要与标签、Markdown 转换、图片下载）
/// 与网页抓取解耦，便于单独测试与扩展
class AiArticleProcessor {
  AiArticleProcessor._();
  static final AiArticleProcessor i = AiArticleProcessor._();

  Future<void> processAll(ArticleModel article) async {
    final tasks = <Future<void>>[
      _processTitle(article),
      _processSummary(article),
      _processMarkdown(article),
      _processImage(article),
    ];
    await Future.wait(tasks);
  }

  Future<void> _processTitle(ArticleModel article) async {
    final articleId = article.id;
    final title = article.title ?? '';
    if (title.isEmpty) {
      logger.w('[AI:标题] 空标题 跳过 #$articleId');
      return;
    }
    try {
      var aiTitle = title;
      if (!StringUtils.isChinese(title)) {
        aiTitle = await AiService.i.translate(title.trim());
      }
      if (aiTitle.length >= AiArticleConstants.longTitleThreshold) {
        aiTitle = await AiService.i.summarizeOneLine(aiTitle);
      }
      if (aiTitle.isEmpty) return;
      await ArticleRepository.updateField(articleId, ArticleFieldName.aiTitle, aiTitle);
      logger.d('[AI:标题] 完成 #$articleId');
    } catch (e) {
      logger.e('[AI:标题] 失败 #$articleId: $e');
    }
  }

  Future<void> _processSummary(ArticleModel article) async {
    final articleId = article.id;
    final content = article.content ?? '';
    if (content.isEmpty) {
      logger.w('[AI:摘要] 空内容 跳过 #$articleId');
      return;
    }
    try {
      final (summary, tags) = await AiService.i.summarize(content.trim());
      if (summary.isEmpty) return;
      await ArticleRepository.updateField(articleId, ArticleFieldName.aiContent, summary);
      await _saveTags(article, tags);
      logger.d('[AI:摘要] 完成 #$articleId');
    } catch (e) {
      logger.e('[AI:摘要] 失败 #$articleId: $e');
    }
  }

  Future<void> _processMarkdown(ArticleModel article) async {
    final articleId = article.id;
    final html = article.htmlContent ?? '';
    if (html.isEmpty) return;
    try {
      final markdown = await AiService.i.convertHtmlToMarkdown(html);
      if (markdown.isEmpty) return;
      await ArticleRepository.updateField(articleId, ArticleFieldName.aiMarkdownContent, markdown);
      logger.d('[AI:Markdown] 完成 #$articleId');
    } catch (e) {
      logger.e('[AI:Markdown] 失败 #$articleId: $e');
    }
  }

  Future<void> _processImage(ArticleModel article) async {
    final articleId = article.id;
    final imageUrl = article.coverImageUrl ?? '';
    if (imageUrl.isEmpty) return;
    try {
      final path = await HttpService.i.downloadImage(imageUrl);
      if (path.isEmpty) return;
      await ArticleRepository.updateField(articleId, ArticleFieldName.coverImage, path);
      logger.d('[AI:图片] 完成 #$articleId');
    } catch (e) {
      logger.e('[AI:图片] 失败 #$articleId: $e');
    }
  }

  Future<void> _saveTags(ArticleModel article, List<String> tagNames) async {
    if (tagNames.isEmpty) return;
    try {
      // 覆盖式设置标签，避免覆盖文章其它字段
      await TagRepository.setTagsForArticle(article.id, tagNames);
      logger.d('[AI:标签] 保存 ${tagNames.length} 个');
    } catch (e) {
      logger.e('[AI:标签] 失败: $e');
    }
  }
}
