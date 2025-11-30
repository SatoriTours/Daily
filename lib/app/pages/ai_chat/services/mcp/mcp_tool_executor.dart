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
      'search_book_notes' => _searchBookNotes(params),
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
    final results = _searchWithKeywords(keyword, (kw) => DiaryRepository.i.findByContent(kw, limit: 50), limit);
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
    final results = _searchWithKeywords(
      keyword,
      (kw) => [
        ...BookRepository.i.findByTitle(kw),
        ...BookRepository.i.findByAuthor(kw),
        ...BookRepository.i.findByCategory(kw),
      ],
      limit,
    );
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

  /// 搜索读书笔记
  String _searchBookNotes(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null) return _errorResponse('缺少参数: keyword');

    final limit = _intParam(params, 'limit', 20);
    final results = _searchWithKeywords(keyword, (kw) => BookViewpointRepository.i.findByContent(kw, limit: 50), limit);

    // 获取关联的书籍信息
    final viewpointsWithBooks = results.map((vp) {
      final book = BookRepository.i.find(vp.bookId);
      return {
        'id': vp.id,
        'title': vp.title,
        'content': _truncate(vp.content, 500),
        'bookId': vp.bookId,
        'bookTitle': book?.title ?? '未知书籍',
        'bookAuthor': book?.author ?? '',
      };
    }).toList();

    return _successResponse({'keyword': keyword, 'notes': viewpointsWithBooks});
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

  /// 多关键词搜索（去重 + 按时间排序）
  ///
  /// [keyword] 搜索关键词（逗号或空格分隔）
  /// [searcher] 搜索函数
  /// [limit] 返回数量限制
  List<T> _searchWithKeywords<T>(String keyword, List<T> Function(String) searcher, int limit) {
    final keywords = _parseKeywords(keyword);
    if (keywords.isEmpty) {
      logger.w('[MCPToolExecutor] 无有效关键词: $keyword');
      return [];
    }

    logger.d('[MCPToolExecutor] 解析关键词: $keywords');
    final resultMap = <int, T>{};

    // 搜索并去重
    for (final kw in keywords) {
      final items = searcher(kw);
      logger.d('[MCPToolExecutor] 关键词 "$kw" 找到 ${items.length} 条');
      for (final item in items) {
        final id = _getId(item);
        if (!resultMap.containsKey(id)) {
          resultMap[id] = item;
        }
      }
    }

    // 按时间倒序排序
    final results = resultMap.values.toList();
    results.sort((a, b) => _getTime(b).compareTo(_getTime(a)));

    final returnCount = results.length > limit ? limit : results.length;
    logger.i('[MCPToolExecutor] 搜索 "$keyword": 共${results.length}条, 返回$returnCount条');

    return results.take(limit).toList();
  }

  /// 解析关键词
  ///
  /// 支持逗号、中文逗号、空格分隔
  /// 中文单字符也允许（如"卡"），英文需要2字符以上
  List<String> _parseKeywords(String keyword) {
    return keyword.split(RegExp(r'[\s,，]+')).map((k) => k.trim().toLowerCase()).where((k) {
      if (k.isEmpty) return false;
      // 中文字符允许单字
      if (_containsChinese(k)) return true;
      // 英文需要至少2个字符
      return k.length >= 2;
    }).toList();
  }

  /// 检查字符串是否包含中文
  bool _containsChinese(String text) {
    return RegExp(r'[\u4e00-\u9fa5]').hasMatch(text);
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
    for (final key in ['diaries', 'articles', 'books', 'viewpoints', 'notes']) {
      if (data[key] is List) return (data[key] as List).length;
    }
    return data['count'] as int? ?? 0;
  }
}
