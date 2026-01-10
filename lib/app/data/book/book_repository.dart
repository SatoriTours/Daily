import 'dart:convert';

import 'package:daily_satori/app/data/book/book.dart';
import 'package:daily_satori/app/data/book/book_search_result.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint.dart';
import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/data/base/base_repository.dart';
import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint_repository.dart';
import 'package:daily_satori/objectbox.g.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/services/book_search_engine/isbndb_search_engine.dart';
import 'package:jinja/jinja.dart';

/// 书籍存储库
///
/// 继承 `BaseRepository<Book, BookModel>` 获取Book的通用CRUD功能
/// 使用单例模式，通过 BookRepository.i 访问
class BookRepository extends BaseRepository<Book, BookModel> {
  // 私有构造函数
  BookRepository._();

  // 单例实例
  static final i = BookRepository._();

  final AiService _aiService = AiService.i;
  final PluginService _pluginService = PluginService.i;

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  BookModel toModel(Book entity) {
    return BookModel(entity);
  }

  // ============ Book 业务查询方法 ============

  /// 根据类别查找书籍
  List<Book> findByCategory(String category) {
    final query = box.query(Book_.category.equals(category)).build();
    return executeQuery(query);
  }

  /// 根据标题查找书籍
  List<Book> findByTitle(String title) {
    final query = box
        .query(Book_.title.contains(title, caseSensitive: false))
        .build();
    return executeQuery(query);
  }

  /// 根据作者查找书籍
  List<Book> findByAuthor(String author) {
    final query = box
        .query(Book_.author.contains(author, caseSensitive: false))
        .build();
    return executeQuery(query);
  }

  // ============ 书籍关联的观点查询 ============

  /// 获取本书的所有观点
  List<BookViewpoint> findViewpoints(int bookId) {
    return BookViewpointRepository.i.findByBookIds([bookId]);
  }

  /// 替换书籍的所有观点
  void replaceViewpoints(int bookId, List<BookViewpoint> newViewpoints) {
    BookViewpointRepository.i.replaceForBook(bookId, newViewpoints);
  }

  // ============ 书籍和观点的 CRUD 操作 ============

  /// 删除书籍及其所有观点
  void deleteBook(int bookId) {
    // 先删除该书籍的所有观点
    final viewpoints = BookViewpointRepository.i.findByBookIds([bookId]);
    if (viewpoints.isNotEmpty) {
      BookViewpointRepository.i.removeMany(
        viewpoints.map((e) => e.id).toList(),
      );
    }
    // 再删除书籍本身
    remove(bookId);
  }

  /// 通过搜索结果添加书籍
  Future<BookModel?> addBookFromSearch(BookSearchResult searchResult) async {
    try {
      // 检查是否已存在相同书名和作者的书籍
      final existingBooks = allModels();
      if (existingBooks.any(
        (book) =>
            book.title.toLowerCase() == searchResult.title.toLowerCase() &&
            book.author.toLowerCase() == searchResult.author.toLowerCase(),
      )) {
        logger.i('书籍已存在（书名和作者相同）: ${searchResult.title}');
        return null;
      }

      // 创建书籍对象
      final book = BookModel.create(
        title: searchResult.title,
        author: searchResult.author,
        category: searchResult.category,
        introduction: searchResult.introduction,
      );

      // 保存书籍
      final bookId = save(book);
      if (bookId <= 0) {
        logger.e('保存书籍失败: ${searchResult.title}');
        return null;
      }

      book.entity.id = bookId;

      // 处理并保存观点
      await _processAndSaveViewpoints(book);

      logger.i('添加书籍成功: ${book.title}');
      return book;
    } catch (e, stackTrace) {
      logger.e(
        '添加书籍失败: ${searchResult.title}',
        error: e,
        stackTrace: stackTrace,
      );
      return null;
    }
  }

  /// 添加书籍（使用AI创建）
  Future<BookModel?> addBook(String title) async {
    try {
      final existingBooks = findByTitle(title);
      if (existingBooks.any((book) => book.title == title)) {
        logger.i('书籍已存在: $title');
        return null;
      }

      final book = await _createBookWithAI(title);
      if (book == null) return null;

      await _processAndSaveViewpoints(book);

      logger.i('添加书籍成功: ${book.title}');
      return book;
    } catch (e, stackTrace) {
      logger.e('添加书籍失败: $title', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  /// 刷新书籍内容：重新拉取书籍信息与观点，并替换原观点
  Future<bool> refreshBook(int bookId) async {
    final book = findModel(bookId);
    if (book == null) return false;

    try {
      // 重新获取书籍信息
      final promptTemplate = _pluginService.bookInfo;
      final prompt = _renderTemplate(promptTemplate, {'title': book.title});
      final response = await _aiService.complete(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      final Map<String, dynamic> bookData = jsonDecode(cleanedResponse);

      // 更新书籍基础信息
      book.author = bookData['author'] as String? ?? book.author;
      book.category = bookData['category'] as String? ?? book.category;
      book.introduction =
          bookData['introduction'] as String? ?? book.introduction;
      book.updatedAt = DateTime.now();
      save(book);

      // 解析观点并替换
      final List<dynamic> viewpointsData =
          bookData['viewpoints'] as List<dynamic>? ?? [];
      List<BookViewpointModel> viewpoints = [];
      if (viewpointsData.isNotEmpty) {
        viewpoints = await _processViewpoints(
          book.id,
          book.title,
          book.author,
          viewpointsData,
        );
      }

      BookViewpointRepository.i.replaceForBook(
        book.id,
        viewpoints.map((e) => e.toEntity()).toList(),
      );
      return true;
    } catch (e, st) {
      logger.e('刷新书籍失败: ${book.title}', error: e, stackTrace: st);
      return false;
    }
  }

  /// 搜索书籍（在线搜索）
  Future<List<BookSearchResult>> searchBooks(String searchTerm) async {
    try {
      logger.i('开始搜索书籍: $searchTerm');

      // 使用 ISBNdb 搜索引擎 (带 Google Books 回退)
      final searchResults = await IsbndbSearchEngine.i.searchBooks(searchTerm);
      return searchResults;
    } catch (e, stackTrace) {
      logger.e('搜索书籍失败: $searchTerm', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  // ============ 私有方法：AI 相关逻辑 ============

  /// 使用AI创建书籍对象
  Future<BookModel?> _createBookWithAI(String title) async {
    try {
      logger.i('开始使用AI创建书籍: $title');

      final promptTemplate = _pluginService.bookInfo;
      logger.d(
        'Book info template: ${promptTemplate.isNotEmpty ? "已加载" : "为空"}',
      );

      final prompt = _renderTemplate(promptTemplate, {'title': title});
      logger.d('Generated prompt length: ${prompt.length}');

      final response = await _aiService.complete(prompt);
      logger.d(
        'AI response received: ${response.isNotEmpty ? response.length : 0} chars',
      );

      if (response.isEmpty) {
        logger.e('AI返回空响应: $title');
        return null;
      }

      final cleanedResponse = _cleanJsonResponse(response);
      logger.d('Cleaned response: ${cleanedResponse.length} chars');

      final Map<String, dynamic> bookData = jsonDecode(cleanedResponse);
      logger.d('Parsed book data keys: ${bookData.keys.toList()}');

      final book = BookModel.create(
        title: bookData['title'] as String? ?? title,
        author: bookData['author'] as String? ?? '未知作者',
        category: bookData['category'] as String? ?? '未分类',
        introduction: bookData['introduction'] as String? ?? '暂无简介',
      );

      final bookId = save(book);
      if (bookId <= 0) {
        logger.e('保存书籍失败: $title');
        return null;
      }

      book.entity.id = bookId;
      logger.i('AI创建书籍成功: ${book.title}');
      return book;
    } catch (e, stackTrace) {
      logger.e('AI创建书籍失败: $title', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  /// 处理并保存书籍观点
  Future<void> _processAndSaveViewpoints(BookModel book) async {
    try {
      final promptTemplate = _pluginService.bookInfo;
      final prompt = _renderTemplate(promptTemplate, {'title': book.title});

      final response = await _aiService.complete(prompt);

      if (response.isEmpty) {
        logger.w('AI返回空响应，无法处理书籍观点: ${book.title}');
        return;
      }

      // 尝试清理和解析JSON响应
      final cleanedResponse = _cleanJsonResponse(response);

      // 添加调试日志
      logger.d('清理后的响应长度: ${cleanedResponse.length}');

      Map<String, dynamic> bookData;
      try {
        bookData = jsonDecode(cleanedResponse);
      } catch (e) {
        logger.e(
          'JSON解析失败，响应内容预览: ${cleanedResponse.substring(0, cleanedResponse.length > 200 ? 200 : cleanedResponse.length)}...',
        );
        rethrow;
      }

      final List<dynamic> viewpointsData =
          bookData['viewpoints'] as List<dynamic>? ?? [];
      if (viewpointsData.isEmpty) {
        logger.w('未找到书籍观点数据: ${book.title}');
        return;
      }

      logger.i('成功解析 ${viewpointsData.length} 个书籍观点: ${book.title}');
      final validViewpoints = await _processViewpoints(
        book.id,
        book.title,
        book.author,
        viewpointsData,
      );

      if (validViewpoints.isNotEmpty) {
        BookViewpointRepository.i.saveMany(validViewpoints);
        logger.i('成功保存 ${validViewpoints.length} 个观点: ${book.title}');
      }
    } catch (e, stackTrace) {
      logger.e('处理书籍观点失败: ${book.title}', error: e, stackTrace: stackTrace);
    }
  }

  /// 处理多个观点
  Future<List<BookViewpointModel>> _processViewpoints(
    int bookId,
    String title,
    String author,
    List<dynamic> viewpointsData,
  ) async {
    final List<Future<BookViewpointModel?>> viewpointFutures = viewpointsData
        .take(20) // 限制最多处理20个观点
        .map(
          (viewpoint) =>
              _processViewpoint(bookId, title, author, viewpoint as String),
        )
        .toList();

    final viewpoints = await Future.wait(viewpointFutures, eagerError: true);
    return viewpoints
        .where((v) => v != null)
        .cast<BookViewpointModel>()
        .toList();
  }

  /// 处理单个观点的详细信息
  Future<BookViewpointModel?> _processViewpoint(
    int bookId,
    String title,
    String author,
    String viewpoint,
  ) async {
    try {
      final promptTemplate = _pluginService.bookViewpoint;
      final prompt = _renderTemplate(promptTemplate, {
        'title': title,
        'author': author,
        'viewpoint': viewpoint,
      });

      final viewpointDetail = await _getViewpointDetailWithRetry(prompt);

      return BookViewpointModel.create(
        bookId: bookId,
        title: viewpoint,
        content: viewpointDetail['content'] as String,
        example: viewpointDetail['example'] as String,
      );
    } catch (e, stackTrace) {
      logger.e(
        '处理观点详情失败: $title - $viewpoint',
        error: e,
        stackTrace: stackTrace,
      );
      return null;
    }
  }

  /// 获取观点详情并支持重试
  Future<Map<String, dynamic>> _getViewpointDetailWithRetry(
    String prompt,
  ) async {
    try {
      final response = await _aiService.complete(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      return jsonDecode(cleanedResponse);
    } catch (e, stackTrace) {
      logger.w('获取观点详情失败，正在重试...', error: e, stackTrace: stackTrace);
      final response = await _aiService.complete(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      return jsonDecode(cleanedResponse);
    }
  }

  /// 渲染模板
  String _renderTemplate(String template, Map<String, String> context) {
    try {
      final env = Environment();
      final tmpl = env.fromString(template);
      return tmpl.render(context);
    } catch (e) {
      logger.e("[BookRepository] 模板渲染失败: $e");
      logger.e("[BookRepository] 模板: $template");
      logger.e("[BookRepository] 上下文: $context");
      return template;
    }
  }

  /// 清理JSON响应中的格式错误
  String _cleanJsonResponse(String response) {
    // 移除可能的代码块标记
    response = response.trim();
    if (response.startsWith('```json')) {
      response = response.substring(7).trim();
    }
    if (response.startsWith('```')) {
      response = response.substring(3).trim();
    }
    if (response.endsWith('```')) {
      response = response.substring(0, response.length - 3).trim();
    }

    // 修复常见的JSON格式错误
    // 1. 移除字符串值末尾的多余引号
    response = response.replaceAll(RegExp(r'""\s*(?=[,\]}])'), '"');

    // 2. 替换省略号为空格（避免 JSON 解析错误）
    response = response.replaceAll('...', ' ');

    // 3. 移除可能导致解析失败的控制字符
    response = response.replaceAll(RegExp(r'[\x00-\x1F\x7F]'), '');

    return response;
  }
}
