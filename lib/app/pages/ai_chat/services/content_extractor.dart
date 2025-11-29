import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/article/article_repository.dart';
import 'package:daily_satori/app/data/diary/diary_repository.dart';
import 'package:daily_satori/app/data/book/book_repository.dart';
import '../models/search_result.dart';

/// 内容提取器
///
/// 负责从搜索结果中提取和处理内容
class ContentExtractor {
  // ========================================================================
  // 常量配置
  // ========================================================================

  /// 内容分析最大长度
  static const int maxContentLength = 6000;

  // ========================================================================
  // 单例模式
  // ========================================================================

  static ContentExtractor? _instance;
  static ContentExtractor get i => _instance ??= ContentExtractor._();
  ContentExtractor._();

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 获取搜索结果的完整内容
  ///
  /// [results] 搜索结果列表
  /// 返回内容映射 (键: 类型:ID, 值: 完整内容)
  Future<Map<String, String>> fetchFullContents(List<SearchResult> results) async {
    logger.d('[ContentExtractor] 开始提取内容，共${results.length}条结果');

    final fullContents = <String, String>{};

    for (final result in results) {
      try {
        final content = _extractContentByType(result);
        if (content != null && content.isNotEmpty) {
          fullContents['${result.type}:${result.id}'] = content;
        }
      } catch (e) {
        logger.e('[ContentExtractor] 内容提取失败: ${result.type}:${result.id}', error: e);
      }
    }

    logger.d('[ContentExtractor] 内容提取完成，共${fullContents.length}条有效内容');
    return fullContents;
  }

  /// 统计搜索结果
  ///
  /// [results] 搜索结果列表
  /// 返回包含各类型数量的统计映射
  Map<String, int> calculateResultStats(List<SearchResult> results) {
    return {
      'articles': results.where((r) => r.type == SearchResultType.article).length,
      'diaries': results.where((r) => r.type == SearchResultType.diary).length,
      'books': results.where((r) => r.type == SearchResultType.book).length,
    };
  }

  /// 合并并限制内容长度
  ///
  /// [contents] 内容映射
  /// 返回合并后的内容字符串
  String mergeContents(Map<String, String> contents) {
    final allContent = contents.values.join('\n\n---\n\n');
    return limitContentLength(allContent, maxContentLength);
  }

  /// 限制内容长度
  ///
  /// [content] 原始内容
  /// [maxLength] 最大长度
  /// 返回限制长度后的内容
  String limitContentLength(String content, int maxLength) {
    if (content.length <= maxLength) return content;
    logger.d('[ContentExtractor] 内容过长，截断至$maxLength字符');
    return content.substring(0, maxLength);
  }

  // ========================================================================
  // 私有方法
  // ========================================================================

  /// 根据类型提取内容
  String? _extractContentByType(SearchResult result) {
    switch (result.type) {
      case SearchResultType.article:
        return _extractArticleContent(result.id);
      case SearchResultType.diary:
        return _extractDiaryContent(result.id);
      case SearchResultType.book:
        return _extractBookContent(result.id);
    }
  }

  /// 提取文章内容
  String? _extractArticleContent(int id) {
    final article = ArticleRepository.i.find(id);
    if (article == null) return null;

    final content = article.aiContent?.isNotEmpty == true
        ? article.aiContent!
        : (article.content?.isNotEmpty == true ? article.content! : '');

    return content.isNotEmpty ? '【文章】${article.title ?? "无标题"}\n\n$content' : null;
  }

  /// 提取日记内容
  String? _extractDiaryContent(int id) {
    final diary = DiaryRepository.i.find(id);
    if (diary == null || diary.content.isEmpty) return null;

    final dateStr =
        '${diary.createdAt.year}-'
        '${diary.createdAt.month.toString().padLeft(2, '0')}-'
        '${diary.createdAt.day.toString().padLeft(2, '0')}';

    return '【日记】$dateStr\n\n${diary.content}';
  }

  /// 提取书籍内容
  String? _extractBookContent(int id) {
    final book = BookRepository.i.find(id);
    if (book == null) return null;

    final intro = book.introduction.isNotEmpty ? book.introduction : '暂无简介';
    return '【书籍】${book.title}\n作者: ${book.author}\n\n$intro';
  }
}
