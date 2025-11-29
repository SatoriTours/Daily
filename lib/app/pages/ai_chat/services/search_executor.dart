import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/article/article_repository.dart';
import 'package:daily_satori/app/data/diary/diary_repository.dart';
import 'package:daily_satori/app/data/book/book_repository.dart';
import '../models/tool_call.dart';
import '../models/search_result.dart';

/// 搜索执行器
///
/// 负责执行具体的搜索操作，包括文章、日记、书籍的搜索
class SearchExecutor {
  // ========================================================================
  // 常量配置
  // ========================================================================

  /// 搜索结果最大数量
  static const int maxSearchResults = 10;

  /// 内容摘要预览长度
  static const int summaryPreviewLength = 150;

  // ========================================================================
  // 单例模式
  // ========================================================================

  static SearchExecutor? _instance;
  static SearchExecutor get i => _instance ??= SearchExecutor._();
  SearchExecutor._();

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 执行工具调用
  ///
  /// [toolCall] 工具调用信息
  /// 返回搜索结果列表
  Future<List<SearchResult>> executeToolCall(ToolCall toolCall) async {
    await Future.delayed(const Duration(milliseconds: 500));

    switch (toolCall.type) {
      case ToolType.searchArticles:
        return searchArticles(toolCall.parameters);
      case ToolType.searchDiary:
        return searchDiary(toolCall.parameters);
      case ToolType.searchBooks:
        return searchBooks(toolCall.parameters);
      case ToolType.searchAll:
        return searchAll(toolCall.parameters);
    }
  }

  /// 执行搜索计划
  ///
  /// [toolPlan] 工具调用计划列表
  /// 返回所有搜索结果
  Future<List<SearchResult>> executeSearchPlan(List<ToolCall> toolPlan) async {
    final allResults = <SearchResult>[];

    for (var i = 0; i < toolPlan.length; i++) {
      final toolCall = toolPlan[i];
      logger.d('[SearchExecutor] 执行任务 ${i + 1}/${toolPlan.length}: ${toolCall.name}');

      final results = await executeToolCall(toolCall);

      if (results.isNotEmpty) {
        logger.d('[SearchExecutor] 任务${i + 1}完成: ${results.length}条结果');
      }
      allResults.addAll(results);
    }

    return allResults;
  }

  // ========================================================================
  // 搜索实现方法
  // ========================================================================

  /// 搜索文章
  List<SearchResult> searchArticles(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    if (keyword == null || keyword.isEmpty) {
      logger.w('[SearchExecutor] 文章搜索: 关键词为空');
      return [];
    }

    logger.d('[SearchExecutor] 搜索文章: $keyword');

    final filters = params['filters'] as Map<String, dynamic>?;
    final keywords = _splitKeywords(keyword);
    final articleMap = <int, dynamic>{};

    for (final kw in keywords) {
      final articles = ArticleRepository.i.findArticles(
        keyword: kw,
        isFavorite: filters?['favorite'] as bool?,
        limit: 20,
      );
      for (final article in articles) {
        articleMap[article.id] = article;
      }
    }

    final results = articleMap.values.take(maxSearchResults).map((article) {
      final summary = _extractArticleSummary(article);
      return SearchResult.fromArticle(
        id: article.id,
        title: article.title ?? '无标题',
        summary: summary,
        createdAt: article.createdAt,
        isFavorite: article.isFavorite,
      );
    }).toList();

    logger.d('[SearchExecutor] 文章搜索完成: ${results.length}条');
    return results;
  }

  /// 搜索日记
  List<SearchResult> searchDiary(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    if (keyword == null || keyword.isEmpty) {
      logger.w('[SearchExecutor] 日记搜索: 关键词为空');
      return [];
    }

    logger.d('[SearchExecutor] 搜索日记: $keyword');

    final keywords = _splitKeywords(keyword);
    final diaryMap = <int, dynamic>{};

    for (final kw in keywords) {
      final diaries = DiaryRepository.i.findByContentPaginated(kw, 1);
      for (final diary in diaries) {
        diaryMap[diary.id] = diary;
      }
    }

    final results = diaryMap.values.take(maxSearchResults).map((diary) {
      final content = diary.content;
      final title = _extractDiaryTitle(content);
      final tags = _extractDiaryTags(diary.tags);

      return SearchResult.fromDiary(
        id: diary.id,
        title: title,
        summary: _limitContentLength(content, summaryPreviewLength),
        createdAt: diary.createdAt,
        tags: tags,
      );
    }).toList();

    logger.d('[SearchExecutor] 日记搜索完成: ${results.length}条');
    return results;
  }

  /// 搜索书籍
  List<SearchResult> searchBooks(Map<String, dynamic> params) {
    final keyword = params['query'] as String?;
    if (keyword == null || keyword.isEmpty) {
      logger.w('[SearchExecutor] 书籍搜索: 关键词为空');
      return [];
    }

    logger.d('[SearchExecutor] 搜索书籍: $keyword');

    final keywords = _splitKeywords(keyword);
    final bookMap = <int, dynamic>{};

    for (final kw in keywords) {
      final books = BookRepository.i.findByTitle(kw);
      for (final book in books) {
        bookMap[book.id] = book;
      }
    }

    final results = bookMap.values.take(maxSearchResults).map((book) {
      return SearchResult.fromBook(
        id: book.id,
        title: book.title,
        summary: '作者: ${book.author}',
        createdAt: book.createdAt,
      );
    }).toList();

    logger.d('[SearchExecutor] 书籍搜索完成: ${results.length}条');
    return results;
  }

  /// 搜索所有内容
  List<SearchResult> searchAll(Map<String, dynamic> params) {
    logger.d('[SearchExecutor] 执行全面搜索');
    return [...searchArticles(params), ...searchDiary(params), ...searchBooks(params)];
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 分割关键词
  List<String> _splitKeywords(String keyword) {
    return keyword.split(' ').where((k) => k.trim().isNotEmpty).toList();
  }

  /// 提取文章摘要
  String? _extractArticleSummary(dynamic article) {
    if (article.aiContent?.isNotEmpty == true) {
      return article.aiContent;
    }
    if (article.content?.isNotEmpty == true) {
      return _limitContentLength(article.content, summaryPreviewLength);
    }
    return null;
  }

  /// 提取日记标题
  String _extractDiaryTitle(String content) {
    final firstLine = content.split('\n').first;
    final title = firstLine.length > 30 ? '${firstLine.substring(0, 30)}...' : firstLine;
    return title.isNotEmpty ? title : '无标题';
  }

  /// 提取日记标签
  List<String>? _extractDiaryTags(String? tagsString) {
    if (tagsString?.isNotEmpty != true) return null;
    return tagsString!.split(',').where((t) => t.trim().isNotEmpty).toList();
  }

  /// 限制内容长度
  String _limitContentLength(String content, int maxLength) {
    if (content.length <= maxLength) return content;
    return content.substring(0, maxLength);
  }
}
