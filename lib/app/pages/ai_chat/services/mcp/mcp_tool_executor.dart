import 'dart:convert';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/article/article_repository.dart';
import 'package:daily_satori/app/data/diary/diary_repository.dart';
import 'package:daily_satori/app/data/book/book_repository.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint_repository.dart';

class MCPToolExecutor {
  static MCPToolExecutor? _instance;
  static MCPToolExecutor get i => _instance ??= MCPToolExecutor._();
  MCPToolExecutor._();

  Future<String> executeTool(String toolName, dynamic arguments) async {
    try {
      return await _dispatchTool(toolName, _parseArguments(arguments));
    } catch (e, stackTrace) {
      logger.e('[MCPToolExecutor] 工具执行失败: $toolName', error: e, stackTrace: stackTrace);
      return _errorResponse('工具执行失败: $e');
    }
  }

  Future<String> _dispatchTool(String toolName, Map<String, dynamic> params) async => switch (toolName) {
    'get_latest_diary' => _getLatestDiary(params),
    'get_diary_by_date' => _getDiaryByDate(params),
    'search_diary_by_content' => _searchDiary(params),
    'get_diary_by_tag' => _getDiaryByTag(params),
    'get_diary_count' => _getDiaryCount(),
    'get_latest_articles' => _getLatestArticles(params),
    'search_articles' => _searchArticles(params),
    'get_favorite_articles' => _getFavoriteArticles(params),
    'get_article_count' => _getArticleCount(),
    'get_latest_books' => _getLatestBooks(params),
    'search_books' => _searchBooks(params),
    'search_book_notes' => _searchBookNotes(params),
    'get_book_viewpoints' => _getBookViewpoints(params),
    'get_book_count' => _getBookCount(),
    'get_statistics' => _getStatistics(),
    _ => _errorResponse('未知工具: $toolName'),
  };

  // 日记工具
  String _getLatestDiary(Map<String, dynamic> params) {
    final diaries = DiaryRepository.i.findAll().take(_intParam(params, 'limit', 5));
    return _successResponse({'diaries': diaries.map(_diaryToMap).toList()});
  }

  String _getDiaryByDate(Map<String, dynamic> params) {
    final dateStr = params['date'] as String?;
    if (dateStr == null) return _errorResponse('缺少参数: date');
    final date = DateTime.tryParse(dateStr);
    if (date == null) return _errorResponse('无效日期格式，请使用 YYYY-MM-DD');
    return _successResponse({
      'date': dateStr,
      'diaries': DiaryRepository.i.findByCreatedDate(date).map(_diaryToMap).toList(),
    });
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
    final diaries = DiaryRepository.i.findByTag(tag).take(_intParam(params, 'limit', 10));
    return _successResponse({'tag': tag, 'diaries': diaries.map(_diaryToMap).toList()});
  }

  String _getDiaryCount() => _successResponse({'count': DiaryRepository.i.count()});

  // 文章工具
  String _getLatestArticles(Map<String, dynamic> params) {
    final articles = ArticleRepository.i.findArticles(limit: _intParam(params, 'limit', 5));
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
    final articles = ArticleRepository.i.findArticles(isFavorite: true, limit: _intParam(params, 'limit', 10));
    return _successResponse({'articles': articles.map(_articleToMap).toList()});
  }

  String _getArticleCount() => _successResponse({'count': ArticleRepository.i.count()});

  // 书籍工具
  String _getLatestBooks(Map<String, dynamic> params) {
    final books = BookRepository.i.all().take(_intParam(params, 'limit', 5));
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

  String _getBookCount() => _successResponse({'count': BookRepository.i.count()});

  String _searchBookNotes(Map<String, dynamic> params) {
    final keyword = params['keyword'] as String?;
    if (keyword == null) return _errorResponse('缺少参数: keyword');
    final limit = _intParam(params, 'limit', 20);
    final results = _searchWithKeywords(keyword, (kw) => BookViewpointRepository.i.findByContent(kw, limit: 50), limit);
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

  // 综合工具
  String _getStatistics() => _successResponse({
    'statistics': {
      'articles': ArticleRepository.i.count(),
      'diaries': DiaryRepository.i.count(),
      'books': BookRepository.i.count(),
    },
  });

  // 通用搜索逻辑
  List<T> _searchWithKeywords<T>(String keyword, List<T> Function(String) searcher, int limit) {
    final keywords = _parseKeywords(keyword);
    if (keywords.isEmpty) return [];

    final resultMap = <int, T>{};
    for (final kw in keywords) {
      for (final item in searcher(kw)) {
        final id = _getId(item);
        if (!resultMap.containsKey(id)) resultMap[id] = item;
      }
    }

    final results = resultMap.values.toList()..sort((a, b) => _getTime(b).compareTo(_getTime(a)));
    return results.take(limit).toList();
  }

  List<String> _parseKeywords(String keyword) => keyword
      .split(RegExp(r'[\s,，]+'))
      .map((k) => k.trim().toLowerCase())
      .where((k) => k.isNotEmpty && (_containsChinese(k) || k.length >= 2))
      .toList();

  bool _containsChinese(String text) => RegExp(r'[\u4e00-\u9fa5]').hasMatch(text);
  int _getId(dynamic item) => item.id as int;
  int _getTime(dynamic item) => (item.createdAt as DateTime?)?.millisecondsSinceEpoch ?? 0;

  // 数据转换
  Map<String, dynamic> _diaryToMap(dynamic d) => {
    'id': d.id,
    'content': _truncate(d.content ?? '', 500),
    'tags': d.tags,
    'createdAt': _formatDate(d.createdAt),
  };

  Map<String, dynamic> _articleToMap(dynamic a) => {
    'id': a.id,
    'title': a.aiTitle ?? a.title ?? '无标题',
    'content': _truncate(a.aiContent ?? '', 800),
    'comment': a.comment ?? '',
    'url': a.url,
    'isFavorite': a.isFavorite,
    'createdAt': _formatDate(a.createdAt),
  };

  Map<String, dynamic> _bookToMap(dynamic b) => {
    'id': b.id,
    'title': b.title,
    'author': b.author,
    'category': b.category,
    'createdAt': _formatDate(b.createdAt),
  };

  // 工具方法
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

  String _formatDate(DateTime? dt) =>
      dt == null ? '' : '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';

  String _truncate(String text, int max) => text.length <= max ? text : '${text.substring(0, max)}...';

  String _successResponse(Map<String, dynamic> data) =>
      jsonEncode({'success': true, 'count': _countItems(data), ...data});

  String _errorResponse(String message) => jsonEncode({'success': false, 'error': message});

  int _countItems(Map<String, dynamic> data) {
    for (final key in ['diaries', 'articles', 'books', 'viewpoints', 'notes']) {
      if (data[key] is List) return (data[key] as List).length;
    }
    return data['count'] as int? ?? 0;
  }
}
