import 'dart:convert';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/article/article_repository.dart';
import 'package:daily_satori/app/data/diary/diary_repository.dart';
import 'package:daily_satori/app/data/book/book_repository.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint_repository.dart';

/// MCP 工具执行器
///
/// 负责执行 AI 调用的工具，连接到 ObjectBox 仓储层
class MCPToolExecutor {
  // ========================================================================
  // 单例模式
  // ========================================================================

  static MCPToolExecutor? _instance;
  static MCPToolExecutor get i => _instance ??= MCPToolExecutor._();
  MCPToolExecutor._();

  // ========================================================================
  // 公共方法
  // ========================================================================

  /// 执行工具调用
  ///
  /// [toolName] 工具名称
  /// [arguments] 工具参数（JSON 字符串或 Map）
  ///
  /// 返回执行结果的 JSON 字符串
  Future<String> executeTool(String toolName, dynamic arguments) async {
    logger.i('[MCPToolExecutor] 执行工具: $toolName');
    logger.d('[MCPToolExecutor] 参数: $arguments');

    try {
      // 解析参数
      final params = _parseArguments(arguments);

      // 根据工具名称分发执行
      final result = await _dispatchTool(toolName, params);

      logger.d('[MCPToolExecutor] 工具执行完成: $toolName');
      return result;
    } catch (e, stackTrace) {
      logger.e('[MCPToolExecutor] 工具执行失败: $toolName', error: e, stackTrace: stackTrace);
      return jsonEncode({'error': '工具执行失败: $e'});
    }
  }

  // ========================================================================
  // 私有方法 - 参数解析
  // ========================================================================

  /// 解析工具参数
  Map<String, dynamic> _parseArguments(dynamic arguments) {
    if (arguments == null) {
      return {};
    }
    if (arguments is Map<String, dynamic>) {
      return arguments;
    }
    if (arguments is String) {
      try {
        return jsonDecode(arguments) as Map<String, dynamic>;
      } catch (_) {
        return {};
      }
    }
    return {};
  }

  // ========================================================================
  // 私有方法 - 工具分发
  // ========================================================================

  /// 分发工具调用到具体实现
  Future<String> _dispatchTool(String toolName, Map<String, dynamic> params) async {
    switch (toolName) {
      // 日记工具
      case 'get_latest_diary':
        return _getLatestDiary(params);
      case 'get_diary_by_date':
        return _getDiaryByDate(params);
      case 'search_diary_by_content':
        return _searchDiaryByContent(params);
      case 'get_diary_by_tag':
        return _getDiaryByTag(params);
      case 'get_diary_count':
        return _getDiaryCount();

      // 文章工具
      case 'get_latest_articles':
        return _getLatestArticles(params);
      case 'search_articles':
        return _searchArticles(params);
      case 'get_favorite_articles':
        return _getFavoriteArticles(params);
      case 'get_article_count':
        return _getArticleCount();

      // 书籍工具
      case 'get_latest_books':
        return _getLatestBooks(params);
      case 'search_books':
        return _searchBooks(params);
      case 'get_book_viewpoints':
        return _getBookViewpoints(params);
      case 'get_book_count':
        return _getBookCount();

      // 综合工具
      case 'get_statistics':
        return _getStatistics();

      default:
        logger.w('[MCPToolExecutor] 未知工具: $toolName');
        return jsonEncode({'error': '未知工具: $toolName'});
    }
  }

  // ========================================================================
  // 日记工具实现
  // ========================================================================

  /// 获取最新日记
  String _getLatestDiary(Map<String, dynamic> params) {
    final limit = _getIntParam(params, 'limit', defaultValue: 1, max: 10);

    logger.i('[MCPToolExecutor] 获取最新 $limit 条日记');

    final diaries = DiaryRepository.i.findAll();
    logger.i('[MCPToolExecutor] 查询到 ${diaries.length} 条日记');

    if (diaries.isEmpty) {
      return jsonEncode({'success': true, 'count': 0, 'diaries': [], 'message': '没有找到任何日记'});
    }

    final results = diaries
        .take(limit)
        .map(
          (diary) => {
            'id': diary.id,
            'content': diary.content,
            'tags': diary.tags,
            'createdAt': _formatDateTime(diary.createdAt),
          },
        )
        .toList();

    logger.i('[MCPToolExecutor] 返回 ${results.length} 条日记');

    return jsonEncode({'success': true, 'count': results.length, 'diaries': results});
  }

  /// 按日期获取日记
  String _getDiaryByDate(Map<String, dynamic> params) {
    final dateStr = params['date'] as String?;
    if (dateStr == null || dateStr.isEmpty) {
      return jsonEncode({'error': '缺少必需参数: date'});
    }

    final date = _parseDate(dateStr);
    if (date == null) {
      return jsonEncode({'error': '无效的日期格式: $dateStr'});
    }

    logger.d('[MCPToolExecutor] 获取日期 $dateStr 的日记');

    final diaries = DiaryRepository.i.findByCreatedDate(date);
    final results = diaries
        .map(
          (diary) => {
            'id': diary.id,
            'content': _truncateContent(diary.content, 500),
            'tags': diary.tags,
            'createdAt': _formatDateTime(diary.createdAt),
          },
        )
        .toList();

    return jsonEncode({'success': true, 'date': dateStr, 'count': results.length, 'diaries': results});
  }

  /// 按内容搜索日记
  String _searchDiaryByContent(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null || keyword.isEmpty) {
      return jsonEncode({'error': '缺少必需参数: keyword'});
    }

    final limit = _getIntParam(params, 'limit', defaultValue: 20, max: 50);

    logger.d('[MCPToolExecutor] 搜索日记: $keyword');

    // 将关键词拆分，支持多关键词搜索
    final keywords = _splitKeywords(keyword);
    final diaryMap = <int, dynamic>{}; // 使用 Map 去重

    // 对每个关键词进行搜索
    for (final kw in keywords) {
      if (kw.isEmpty) continue;
      logger.d('[MCPToolExecutor] 搜索关键词: $kw');
      final diaries = DiaryRepository.i.findByContent(kw);
      for (final diary in diaries) {
        if (!diaryMap.containsKey(diary.id)) {
          diaryMap[diary.id] = diary;
        }
      }
    }

    // 按创建时间排序并限制数量
    final sortedDiaries = diaryMap.values.toList()
      ..sort((a, b) => (b.createdAt?.millisecondsSinceEpoch ?? 0).compareTo(a.createdAt?.millisecondsSinceEpoch ?? 0));

    final results = sortedDiaries
        .take(limit)
        .map(
          (diary) => {
            'id': diary.id,
            'content': _truncateContent(diary.content, 500),
            'tags': diary.tags,
            'createdAt': _formatDateTime(diary.createdAt),
          },
        )
        .toList();

    logger.d('[MCPToolExecutor] 搜索完成: 关键词数=${keywords.length}, 结果数=${results.length}');

    return jsonEncode({'success': true, 'keyword': keyword, 'count': results.length, 'diaries': results});
  }

  /// 按标签获取日记
  String _getDiaryByTag(Map<String, dynamic> params) {
    final tag = params['tag'] as String?;
    if (tag == null || tag.isEmpty) {
      return jsonEncode({'error': '缺少必需参数: tag'});
    }

    final limit = _getIntParam(params, 'limit', defaultValue: 10, max: 50);

    logger.d('[MCPToolExecutor] 获取标签 $tag 的日记');

    final diaries = DiaryRepository.i.findByTag(tag);
    final results = diaries
        .take(limit)
        .map(
          (diary) => {
            'id': diary.id,
            'content': _truncateContent(diary.content, 500),
            'tags': diary.tags,
            'createdAt': _formatDateTime(diary.createdAt),
          },
        )
        .toList();

    return jsonEncode({'success': true, 'tag': tag, 'count': results.length, 'diaries': results});
  }

  /// 获取日记数量
  String _getDiaryCount() {
    final count = DiaryRepository.i.count();
    logger.d('[MCPToolExecutor] 日记总数: $count');

    return jsonEncode({'success': true, 'count': count});
  }

  // ========================================================================
  // 文章工具实现
  // ========================================================================

  /// 获取最新文章
  String _getLatestArticles(Map<String, dynamic> params) {
    final limit = _getIntParam(params, 'limit', defaultValue: 5, max: 20);

    logger.d('[MCPToolExecutor] 获取最新 $limit 篇文章');

    final articles = ArticleRepository.i.findArticles(limit: limit);
    final results = articles
        .map(
          (article) => {
            'id': article.id,
            'title': article.title ?? article.aiTitle ?? '无标题',
            'summary': _truncateContent(article.aiContent ?? article.content ?? '', 300),
            'url': article.url,
            'isFavorite': article.isFavorite,
            'createdAt': _formatDateTime(article.createdAt),
          },
        )
        .toList();

    return jsonEncode({'success': true, 'count': results.length, 'articles': results});
  }

  /// 搜索文章
  String _searchArticles(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null || keyword.isEmpty) {
      return jsonEncode({'error': '缺少必需参数: keyword'});
    }

    final limit = _getIntParam(params, 'limit', defaultValue: 20, max: 50);

    logger.d('[MCPToolExecutor] 搜索文章: $keyword');

    // 将关键词拆分，支持多关键词搜索
    final keywords = _splitKeywords(keyword);
    final articleMap = <int, dynamic>{}; // 使用 Map 去重

    // 对每个关键词进行搜索
    for (final kw in keywords) {
      if (kw.isEmpty) continue;
      logger.d('[MCPToolExecutor] 搜索关键词: $kw');
      final articles = ArticleRepository.i.findArticles(keyword: kw, limit: 20);
      for (final article in articles) {
        // 用 ID 去重
        if (!articleMap.containsKey(article.id)) {
          articleMap[article.id] = article;
        }
      }
    }

    // 按创建时间排序并限制数量
    final sortedArticles = articleMap.values.toList()
      ..sort((a, b) => (b.createdAt?.millisecondsSinceEpoch ?? 0).compareTo(a.createdAt?.millisecondsSinceEpoch ?? 0));

    final results = sortedArticles
        .take(limit)
        .map(
          (article) => {
            'id': article.id,
            'title': article.title ?? article.aiTitle ?? '无标题',
            'summary': _truncateContent(article.aiContent ?? article.content ?? '', 300),
            'url': article.url,
            'isFavorite': article.isFavorite,
            'createdAt': _formatDateTime(article.createdAt),
          },
        )
        .toList();

    logger.d('[MCPToolExecutor] 搜索完成: 关键词数=${keywords.length}, 结果数=${results.length}');

    return jsonEncode({'success': true, 'keyword': keyword, 'count': results.length, 'articles': results});
  }

  /// 拆分关键词
  ///
  /// 支持空格、逗号分隔的多关键词
  List<String> _splitKeywords(String keyword) {
    // 按空格和逗号分割
    final parts = keyword.split(RegExp(r'[\s,，]+'));
    // 过滤空字符串和常见停用词
    final stopWords = {'的', '了', '是', '在', '和', '与', '或', '如何', '怎么', '什么', '哪些', '哪个'};
    return parts.map((p) => p.trim()).where((p) => p.isNotEmpty && !stopWords.contains(p)).toList();
  }

  /// 获取标记为喜爱的文章
  String _getFavoriteArticles(Map<String, dynamic> params) {
    final limit = _getIntParam(params, 'limit', defaultValue: 10, max: 50);

    logger.d('[MCPToolExecutor] 获取喜爱的文章 (isFavorite=true)');

    final articles = ArticleRepository.i.findArticles(isFavorite: true, limit: limit);
    final results = articles
        .map(
          (article) => {
            'id': article.id,
            'title': article.title ?? article.aiTitle ?? '无标题',
            'summary': _truncateContent(article.aiContent ?? article.content ?? '', 300),
            'url': article.url,
            'createdAt': _formatDateTime(article.createdAt),
          },
        )
        .toList();

    return jsonEncode({'success': true, 'count': results.length, 'articles': results});
  }

  /// 获取文章数量
  String _getArticleCount() {
    final count = ArticleRepository.i.count();
    logger.d('[MCPToolExecutor] 文章总数: $count');

    return jsonEncode({'success': true, 'count': count});
  }

  // ========================================================================
  // 书籍工具实现
  // ========================================================================

  /// 获取最新书籍
  String _getLatestBooks(Map<String, dynamic> params) {
    final limit = _getIntParam(params, 'limit', defaultValue: 5, max: 20);

    logger.d('[MCPToolExecutor] 获取最新 $limit 本书');

    final books = BookRepository.i.all();
    final results = books
        .take(limit)
        .map(
          (book) => {
            'id': book.id,
            'title': book.title,
            'author': book.author,
            'category': book.category,
            'createdAt': _formatDateTime(book.createdAt),
          },
        )
        .toList();

    return jsonEncode({'success': true, 'count': results.length, 'books': results});
  }

  /// 搜索书籍
  String _searchBooks(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null || keyword.isEmpty) {
      return jsonEncode({'error': '缺少必需参数: keyword'});
    }

    final limit = _getIntParam(params, 'limit', defaultValue: 20, max: 50);

    logger.d('[MCPToolExecutor] 搜索书籍: $keyword');

    // 将关键词拆分，支持多关键词搜索
    final keywords = _splitKeywords(keyword);
    final bookMap = <int, dynamic>{}; // 使用 Map 去重

    // 对每个关键词进行搜索
    for (final kw in keywords) {
      if (kw.isEmpty) continue;
      logger.d('[MCPToolExecutor] 搜索关键词: $kw');
      // 搜索书名和作者
      final byTitle = BookRepository.i.findByTitle(kw);
      final byAuthor = BookRepository.i.findByAuthor(kw);
      for (final book in [...byTitle, ...byAuthor]) {
        if (!bookMap.containsKey(book.id)) {
          bookMap[book.id] = book;
        }
      }
    }

    // 按创建时间排序并限制数量
    final sortedBooks = bookMap.values.toList()
      ..sort((a, b) => (b.createdAt?.millisecondsSinceEpoch ?? 0).compareTo(a.createdAt?.millisecondsSinceEpoch ?? 0));

    final results = sortedBooks
        .take(limit)
        .map(
          (book) => {
            'id': book.id,
            'title': book.title,
            'author': book.author,
            'category': book.category,
            'createdAt': _formatDateTime(book.createdAt),
          },
        )
        .toList();

    logger.d('[MCPToolExecutor] 搜索完成: 关键词数=${keywords.length}, 结果数=${results.length}');

    return jsonEncode({'success': true, 'keyword': keyword, 'count': results.length, 'books': results});
  }

  /// 获取书籍的观点/笔记
  String _getBookViewpoints(Map<String, dynamic> params) {
    final bookId = params['book_id'] as int?;
    if (bookId == null) {
      return jsonEncode({'error': '缺少必需参数: book_id'});
    }

    logger.d('[MCPToolExecutor] 获取书籍 #$bookId 的观点');

    final book = BookRepository.i.find(bookId);
    if (book == null) {
      return jsonEncode({'error': '未找到书籍: $bookId'});
    }

    final viewpoints = BookViewpointRepository.i.findByBookIds([bookId]);
    final results = viewpoints
        .map((vp) => {'id': vp.id, 'title': vp.title, 'content': vp.content, 'example': vp.example})
        .toList();

    return jsonEncode({
      'success': true,
      'book': {'id': book.id, 'title': book.title, 'author': book.author},
      'count': results.length,
      'viewpoints': results,
    });
  }

  /// 获取书籍数量
  String _getBookCount() {
    final count = BookRepository.i.count();
    logger.d('[MCPToolExecutor] 书籍总数: $count');

    return jsonEncode({'success': true, 'count': count});
  }

  // ========================================================================
  // 综合工具实现
  // ========================================================================

  /// 获取统计信息
  String _getStatistics() {
    logger.d('[MCPToolExecutor] 获取综合统计');

    final articleCount = ArticleRepository.i.count();
    final diaryCount = DiaryRepository.i.count();
    final bookCount = BookRepository.i.count();

    return jsonEncode({
      'success': true,
      'statistics': {
        'articles': articleCount,
        'diaries': diaryCount,
        'books': bookCount,
        'total': articleCount + diaryCount + bookCount,
      },
    });
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 获取整数参数
  int _getIntParam(Map<String, dynamic> params, String key, {required int defaultValue, int? max}) {
    final value = params[key];
    int result = defaultValue;

    if (value is int) {
      result = value;
    } else if (value is String) {
      result = int.tryParse(value) ?? defaultValue;
    }

    if (max != null && result > max) {
      result = max;
    }

    return result;
  }

  /// 解析日期
  DateTime? _parseDate(String dateStr) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);

    // 处理相对日期
    switch (dateStr.toLowerCase()) {
      case 'today':
        return today;
      case 'yesterday':
        return today.subtract(const Duration(days: 1));
      default:
        // 尝试解析 YYYY-MM-DD 格式
        try {
          return DateTime.parse(dateStr);
        } catch (_) {
          return null;
        }
    }
  }

  /// 格式化日期时间
  String _formatDateTime(DateTime? dateTime) {
    if (dateTime == null) return '';
    return '${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')} '
        '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}';
  }

  /// 截断内容
  String _truncateContent(String content, int maxLength) {
    if (content.length <= maxLength) return content;
    return '${content.substring(0, maxLength)}...';
  }
}
