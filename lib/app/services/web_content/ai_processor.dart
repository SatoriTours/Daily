import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';

/// AI处理器
/// 专门负责处理AI相关的任务：翻译、摘要、标签提取、Markdown转换等
class AiProcessor {
  /// 处理文章标题
  Future<String> processTitle(String title) async {
    if (title.isEmpty) {
      logger.w('[AI:标题] 标题为空，跳过处理');
      return title;
    }

    logger.i('[AI:标题] ▶ 开始处理标题');

    try {
      var processedTitle = title;

      // 如果不是中文，进行翻译
      final hasChinese = RegExp(r'[\u4e00-\u9fff]').hasMatch(title);
      if (!hasChinese) {
        processedTitle = await AiService.i.translate(title.trim());
      }

      // 如果标题太长，进行概括
      if (processedTitle.length >= 50) {
        logger.d('[AI:标题] 标题太长，进行概括');
        processedTitle = await AiService.i.summarizeOneLine(processedTitle);
      }

      logger.i('[AI:标题] ◀ 标题处理成功: $processedTitle');
      return processedTitle;
    } catch (e) {
      logger.e('[AI:标题] 处理失败: $e');
      throw Exception('处理标题失败: $e');
    }
  }

  /// 处理文章摘要和标签
  Future<(String, List<String>)> processSummaryAndTags(String content) async {
    if (content.isEmpty) {
      logger.w('[AI:摘要] 内容为空，跳过处理');
      return ('', <String>[]);
    }

    logger.i('[AI:摘要] ▶ 开始处理摘要和标签');

    try {
      final (summary, tagsDynamic) = await AiService.i.summarize(content.trim());
      final List<String> tags = tagsDynamic.map((e) => e.toString()).toList();

      if (summary.isEmpty) {
        logger.w('[AI:摘要] 处理结果为空');
        return ('', <String>[]);
      }

      logger.i('[AI:摘要] ◀ 摘要处理成功，提取了 ${tags.length} 个标签');
      return (summary, tags);
    } catch (e) {
      logger.e('[AI:摘要] 处理失败: $e');
      throw Exception('处理摘要失败: $e');
    }
  }

  /// 处理Markdown转换
  Future<String> processMarkdown(String htmlContent) async {
    if (htmlContent.isEmpty) {
      logger.w('[AI:Markdown] HTML内容为空，跳过处理');
      return '';
    }

    logger.i('[AI:Markdown] ▶ 开始处理Markdown');

    try {
      final markdown = await AiService.i.convertHtmlToMarkdown(htmlContent);

      if (markdown.isEmpty) {
        logger.w('[AI:Markdown] 处理结果为空');
        return '';
      }

      logger.i('[AI:Markdown] ◀ Markdown处理成功');
      return markdown;
    } catch (e) {
      logger.e('[AI:Markdown] 处理失败: $e');
      throw Exception('处理Markdown失败: $e');
    }
  }

  /// 批量处理AI任务
  Future<void> processAllAiTasks(ArticleModel article) async {
    final articleId = article.id;
    logger.i('[AI:批量处理] ▶ 开始处理所有AI任务 #$articleId');

    try {
      // 创建任务列表
      final tasks = [_processTitleTask(article), _processSummaryTask(article), _processMarkdownTask(article)];

      // 并行执行所有AI处理任务
      await Future.wait(tasks);

      logger.i('[AI:批量处理] ◀ 所有AI任务处理完成 #$articleId');
    } catch (e) {
      logger.e('[AI:批量处理] 处理失败 #$articleId: $e');
      throw Exception('AI批量处理失败: $e');
    }
  }

  Future<void> _processTitleTask(ArticleModel article) async {
    final processedTitle = await processTitle(article.title ?? '');
    article.aiTitle = processedTitle;
    ArticleRepository.i.updateModel(article);
  }

  Future<void> _processSummaryTask(ArticleModel article) async {
    final (summary, tags) = await processSummaryAndTags(article.content ?? '');
    article.aiContent = summary;
    ArticleRepository.i.updateModel(article);

    // 处理标签
    if (tags.isNotEmpty) {
      article.tags.clear();
      for (var tagName in tags) {
        await TagRepository.i.addTagToArticle(article, tagName);
      }
    }
  }

  Future<void> _processMarkdownTask(ArticleModel article) async {
    final markdown = await processMarkdown(article.htmlContent ?? '');
    article.aiMarkdownContent = markdown;
    ArticleRepository.i.updateModel(article);
  }
}
