import 'dart:convert';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/article/article_repository.dart';
import 'package:daily_satori/app/data/diary/diary_repository.dart';
import 'package:daily_satori/app/data/book/book_repository.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint_repository.dart';

/// MCP 工具执行器
///
/// 负责执行 AI 调用的工具，连接到 ObjectBox 仓储层
/// 设计原则：代码只负责数据查询和格式化，业务逻辑（如关键词处理、日期解析）交给 AI
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
  Future<String> executeTool(String toolName, dynamic arguments) async {
    logger.i('[MCPToolExecutor] 执行工具: $toolName');

    try {
      final params = _parseArguments(arguments);
      final result = await _dispatchTool(toolName, params);
      return result;
    } catch (e, stackTrace) {
      logger.e('[MCPToolExecutor] 工具执行失败: $toolName', error: e, stackTrace: stackTrace);
      return _errorResponse('工具执行失败: $e');
    }
  }

  // ========================================================================
  // 工具分发
  // ========================================================================

  Future<String> _dispatchTool(String toolName, Map<String, dynamic> params) async {
    return switch (toolName) {
      // 日记
      'get_latest_diary' => _getLatestDiary(params),
      'get_diary_by_date' => _getDiaryByDate(params),
      'search_diary_by_content' => _searchDiary(params),
      'get_diary_by_tag' => _getDiaryByTag(params),
      'get_diary_count' => _getDiaryCount(),
      // 文章
      'get_latest_articles' => _getLatestArticles(params),
      'search_articles' => _searchArticles(params),
      'get_favorite_articles' => _getFavoriteArticles(params),
      'get_article_count' => _getArticleCount(),
      // 书籍
      'get_latest_books' => _getLatestBooks(params),
      'search_books' => _searchBooks(params),
      'get_book_viewpoints' => _getBookViewpoints(params),
      'get_book_count' => _getBookCount(),
      // 综合
      'get_statistics' => _getStatistics(),
      _ => _errorResponse('未知工具: $toolName'),
    };
  }

  // ========================================================================
  // 日记工具
  // ========================================================================

  String _getLatestDiary(Map<String, dynamic> params) {
    final limit = _intParam(params, 'limit', 5);
    final diaries = DiaryRepository.i.findAll().take(limit);
    return _successResponse({'diaries': diaries.map(_diaryToMap).toList()});
  }

  String _getDiaryByDate(Map<String, dynamic> params) {
    final dateStr = params['date'] as String?;
    if (dateStr == null) return _errorResponse('缺少参数: date');

    // AI 应该传递 YYYY-MM-DD 格式的日期
    final date = DateTime.tryParse(dateStr);
    if (date == null) return _errorResponse('无效日期格式，请使用 YYYY-MM-DD');

    final diaries = DiaryRepository.i.findByCreatedDate(date);
    return _successResponse({'date': dateStr, 'diaries': diaries.map(_diaryToMap).toList()});
  }

  String _searchDiary(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null) return _errorResponse('缺少参数: keyword');

    final limit = _intParam(params, 'limit', 20);
    final results = _searchWithKeywords(keyword, (kw) => DiaryRepository.i.findByContent(kw), limit);
    return _successResponse({'keyword': keyword, 'diaries': results.map(_diaryToMap).toList()});
  }

  String _getDiaryByTag(Map<String, dynamic> params) {
    final tag = params['tag'] as String?;
    if (tag == null) return _errorResponse('缺少参数: tag');

    final limit = _intParam(params, 'limit', 10);
    final diaries = DiaryRepository.i.findByTag(tag).take(limit);
    return _successResponse({'tag': tag, 'diaries': diaries.map(_diaryToMap).toList()});
  }

  String _getDiaryCount() {
    return _successResponse({'count': DiaryRepository.i.count()});
  }

  // ========================================================================
  // 文章工具
  // ========================================================================

  String _getLatestArticles(Map<String, dynamic> params) {
    final limit = _intParam(params, 'limit', 5);
    final articles = ArticleRepository.i.findArticles(limit: limit);
    return _successResponse({'articles': articles.map(_articleToMap).toList()});
  }

  String _searchArticles(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null) return _errorResponse('缺少参数: keyword');

    final limit = _intParam(params, 'limit', 20);
    // 每个关键词搜索更多结果，以便去重后仍有足够数量
    final results = _searchWithKeywords(
      keyword,
      (kw) => ArticleRepository.i.findArticles(keyword: kw, limit: 50),
      limit,
    );
    return _successResponse({'keyword': keyword, 'articles': results.map(_articleToMap).toList()});
  }

  String _getFavoriteArticles(Map<String, dynamic> params) {
    final limit = _intParam(params, 'limit', 10);
    final articles = ArticleRepository.i.findArticles(isFavorite: true, limit: limit);
    return _successResponse({'articles': articles.map(_articleToMap).toList()});
  }

  String _getArticleCount() {
    return _successResponse({'count': ArticleRepository.i.count()});
  }

  // ========================================================================
  // 书籍工具
  // ========================================================================

  String _getLatestBooks(Map<String, dynamic> params) {
    final limit = _intParam(params, 'limit', 5);
    final books = BookRepository.i.all().take(limit);
    return _successResponse({'books': books.map(_bookToMap).toList()});
  }

  String _searchBooks(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null) return _errorResponse('缺少参数: keyword');

    final limit = _intParam(params, 'limit', 20);
    final results = _searchWithKeywords(keyword, (kw) {
      return [...BookRepository.i.findByTitle(kw), ...BookRepository.i.findByAuthor(kw)];
    }, limit);
    return _successResponse({'keyword': keyword, 'books': results.map(_bookToMap).toList()});
  }

  String _getBookViewpoints(Map<String, dynamic> params) {
    final bookId = params['book_id'] as int?;
    if (bookId == null) return _errorResponse('缺少参数: book_id');

    final book = BookRepository.i.find(bookId);
    if (book == null) return _errorResponse('未找到书籍: $bookId');

    final viewpoints = BookViewpointRepository.i.findByBookIds([bookId]);
    return _successResponse({
      'book': {'id': book.id, 'title': book.title, 'author': book.author},
      'viewpoints': viewpoints.map((vp) => {'id': vp.id, 'title': vp.title, 'content': vp.content}).toList(),
    });
  }

  String _getBookCount() {
    return _successResponse({'count': BookRepository.i.count()});
  }

  // ========================================================================
  // 综合工具
  // ========================================================================

  String _getStatistics() {
    return _successResponse({
      'statistics': {
        'articles': ArticleRepository.i.count(),
        'diaries': DiaryRepository.i.count(),
        'books': BookRepository.i.count(),
      },
    });
  }

  // ========================================================================
  // 通用搜索逻辑
  // ========================================================================

  /// 多关键词搜索（去重 + 排序）
  List<T> _searchWithKeywords<T>(String keyword, List<T> Function(String) searcher, int limit) {
    final keywords = keyword.split(RegExp(r'[\s,，]+')).where((k) => k.isNotEmpty);
    final resultMap = <int, T>{};

    for (final kw in keywords) {
      for (final item in searcher(kw)) {
        final id = _getId(item);
        resultMap.putIfAbsent(id, () => item);
      }
    }

    // 按时间倒序
    final sorted = resultMap.values.toList()..sort((a, b) => _getTime(b).compareTo(_getTime(a)));
    return sorted.take(limit).toList();
  }

  int _getId(dynamic item) => item.id as int;
  int _getTime(dynamic item) => (item.createdAt as DateTime?)?.millisecondsSinceEpoch ?? 0;

  // ========================================================================
  // 数据转换
  // ========================================================================

  Map<String, dynamic> _diaryToMap(dynamic diary) => {
    'id': diary.id,
    'content': _truncate(diary.content ?? '', 500),
    'tags': diary.tags,
    'createdAt': _formatDate(diary.createdAt),
  };

  Map<String, dynamic> _articleToMap(dynamic article) => {
    'id': article.id,
    'title': article.title ?? article.aiTitle ?? '无标题',
    'summary': _truncate(article.aiContent ?? article.content ?? '', 300),
    'url': article.url,
    'isFavorite': article.isFavorite,
    'createdAt': _formatDate(article.createdAt),
  };

  Map<String, dynamic> _bookToMap(dynamic book) => {
    'id': book.id,
    'title': book.title,
    'author': book.author,
    'category': book.category,
    'createdAt': _formatDate(book.createdAt),
  };

  // ========================================================================
  // 工具方法
  // ========================================================================

  Map<String, dynamic> _parseArguments(dynamic args) {
    if (args == null) return {};
    if (args is Map<String, dynamic>) return args;
    if (args is String) {
      try {
        return jsonDecode(args) as Map<String, dynamic>;
      } catch (_) {}
    }
    return {};
  }

  int _intParam(Map<String, dynamic> params, String key, int defaultValue) {
    final value = params[key];
    if (value is int) return value;
    if (value is String) return int.tryParse(value) ?? defaultValue;
    return defaultValue;
  }

  String _formatDate(DateTime? dt) {
    if (dt == null) return '';
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';
  }

  String _truncate(String text, int max) => text.length <= max ? text : '${text.substring(0, max)}...';

  String _successResponse(Map<String, dynamic> data) {
    return jsonEncode({'success': true, 'count': _countItems(data), ...data});
  }

  String _errorResponse(String message) => jsonEncode({'success': false, 'error': message});

  int _countItems(Map<String, dynamic> data) {
    for (final key in ['diaries', 'articles', 'books', 'viewpoints']) {
      if (data[key] is List) return (data[key] as List).length;
    }
    return data['count'] as int? ?? 0;
  }
}
