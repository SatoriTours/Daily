import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/services/book_search_engine/open_library_search_engine.dart';
import 'dart:convert';

import 'package:jinja/jinja.dart';

/// 书籍服务
///
/// 负责处理与书籍相关的业务逻辑，包括AI推荐、书籍信息获取等
class BookService {
  static final BookService _instance = BookService._();
  static BookService get i => _instance;

  BookService._();

  final AiService _aiService = AiService.i;
  final PluginService _pluginService = PluginService.i;

  /// 初始化
  Future<void> init() async {
    // if (!AppInfoUtils.isProduction) {
    //   await BookRepository.i.deleteAllSync();
    // }
  }

  /// 获取所有书籍
  List<BookModel> getBooks() {
    return BookRepository.i.all();
  }

  /// 按分类获取书籍
  Future<List<BookModel>> getBooksByCategory(String category) async {
    final books = BookRepository.i.findByCategory(category);
    return books.map((e) => BookModel(e)).toList();
  }

  /// 通过分类获取推荐书籍
  ///
  /// [category] 书籍分类
  /// 返回该分类下的推荐书籍列表
  Future<List<BookModel>> getRecommendedBooksByCategory(String category) async {
    try {
      final existingTitles = _getExistingBookTitles();
      final recommendedBooks = await _fetchRecommendedBooks(category, existingTitles);

      if (recommendedBooks.isNotEmpty) {
        await BookRepository.i.saveMany(recommendedBooks);
      }

      return recommendedBooks;
    } catch (e, stackTrace) {
      logger.e('获取分类推荐书籍失败: $category', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 获取已存在的书籍标题集合
  Set<String> _getExistingBookTitles() {
    final existingBooks = BookRepository.i.all();
    return existingBooks.map((book) => book.title.toLowerCase()).toSet();
  }

  /// 从AI获取推荐书籍
  Future<List<BookModel>> _fetchRecommendedBooks(String category, Set<String> existingTitles) async {
    final promptTemplate = _pluginService.getBookRecommendByCategory();
    final prompt = _renderTemplate(promptTemplate, {'category': category});

    final response = await _aiService.getCompletion(prompt);
    final cleanedResponse = _cleanJsonResponse(response);
    final List<dynamic> booksData = jsonDecode(cleanedResponse);

    final List<BookModel> recommendedBooks = [];
    for (final bookData in booksData) {
      final title = bookData['title'] as String;
      if (!existingTitles.contains(title.toLowerCase())) {
        recommendedBooks.add(_createBookFromData(bookData, category));
      }
    }

    return recommendedBooks;
  }

  /// 从数据创建书籍模型
  BookModel _createBookFromData(dynamic bookData, String category) {
    return BookModel.create(
      title: bookData['title'] as String,
      author: bookData['author'] as String,
      category: category,
      introduction: bookData['introduction'] as String,
    );
  }

  /// 获取书籍的关键观点
  ///
  /// [book] 书籍对象
  /// 返回该书籍的关键观点列表
  Future<BookViewpointModel?> getBookViewpoint(BookModel book, String viewpoint) async {
    try {
      // 使用AI获取书籍的关键观点
      final promptTemplate = _pluginService.getBookViewpoint();
      final prompt = _renderTemplate(promptTemplate, {
        'title': book.title,
        'author': book.author,
        'viewpoint': viewpoint,
      });

      final response = await _aiService.getCompletion(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      final Map<String, dynamic> viewpointData = jsonDecode(cleanedResponse);

      // 将观点数据转换为BookViewpoint对象
      return BookViewpointModel.create(
        bookId: book.id,
        title: viewpointData['title'] as String,
        content: viewpointData['content'] as String,
        example: viewpointData['example'] as String,
      );
    } catch (e, stackTrace) {
      logger.e('获取书籍观点失败: ${book.title} ${stackTrace.toString()}', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  /// 处理单个观点的详细信息
  ///
  /// [bookId] 书籍ID
  /// [title] 书籍标题
  /// [author] 书籍作者
  /// [viewpoint] 观点数据
  /// 返回处理后的观点模型
  Future<BookViewpointModel?> _processViewpoint(int bookId, String title, String author, String viewpoint) async {
    try {
      final promptTemplate = _pluginService.getBookViewpoint();
      final prompt = _renderTemplate(promptTemplate, {'title': title, 'author': author, 'viewpoint': viewpoint});

      final viewpointDetail = await _getViewpointDetailWithRetry(prompt);

      return BookViewpointModel.create(
        bookId: bookId,
        title: viewpoint,
        content: viewpointDetail['content'] as String,
        example: viewpointDetail['example'] as String,
      );
    } catch (e, stackTrace) {
      logger.e('处理观点详情失败: $title - $viewpoint', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  /// 获取观点详情并支持重试
  Future<Map<String, dynamic>> _getViewpointDetailWithRetry(String prompt) async {
    try {
      final response = await _aiService.getCompletion(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      return jsonDecode(cleanedResponse);
    } catch (e, stackTrace) {
      logger.w('获取观点详情失败，正在重试...', error: e, stackTrace: stackTrace);
      final response = await _aiService.getCompletion(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      return jsonDecode(cleanedResponse);
    }
  }

  /// 搜索书籍
  ///
  /// [searchTerm] 搜索关键词
  /// 返回搜索到的书籍列表
  Future<List<BookSearchResult>> searchBooks(String searchTerm) async {
    try {
      logger.i('开始搜索书籍: $searchTerm');

      // 使用 OpenLibrary 搜索引擎
      final searchResults = await OpenLibrarySearchEngine.i.searchBooks(searchTerm);
      return searchResults;
    } catch (e, stackTrace) {
      logger.e('搜索书籍失败: $searchTerm', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 通过选择的搜索结果添加书籍
  ///
  /// [searchResult] 用户选择的书籍搜索结果
  /// 返回添加的书籍
  Future<BookModel?> addBookFromSearch(BookSearchResult searchResult) async {
    try {
      // 检查是否已存在相同书名和作者的书籍
      final existingBooks = BookRepository.i.all();
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
      final bookId = BookRepository.i.save(book);
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
      logger.e('添加书籍失败: ${searchResult.title}', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  /// 添加书籍（直接创建，兼容原有功能）
  ///
  /// [title] 书名
  /// [category] 分类名（可选）
  /// 返回添加的书籍
  Future<BookModel?> addBook(String title) async {
    try {
      final existingBooks = BookRepository.i.findByTitle(title);
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

  /// 使用AI创建书籍对象
  Future<BookModel?> _createBookWithAI(String title) async {
    try {
      logger.i('开始使用AI创建书籍: $title');

      final promptTemplate = _pluginService.getBookInfo();
      logger.d('Book info template: ${promptTemplate.isNotEmpty ? "已加载" : "为空"}');

      final prompt = _renderTemplate(promptTemplate, {'title': title});
      logger.d('Generated prompt length: ${prompt.length}');

      final response = await _aiService.getCompletion(prompt);
      logger.d('AI response received: ${response.isNotEmpty ? response.length : 0} chars');

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

      final bookId = BookRepository.i.save(book);
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
      final promptTemplate = _pluginService.getBookInfo();
      final prompt = _renderTemplate(promptTemplate, {'title': book.title});

      final response = await _aiService.getCompletion(prompt);

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

      final List<dynamic> viewpointsData = bookData['viewpoints'] as List<dynamic>? ?? [];
      if (viewpointsData.isEmpty) {
        logger.w('未找到书籍观点数据: ${book.title}');
        return;
      }

      logger.i('成功解析 ${viewpointsData.length} 个书籍观点: ${book.title}');
      final validViewpoints = await _processViewpoints(book.id, book.title, book.author, viewpointsData);

      if (validViewpoints.isNotEmpty) {
        await BookViewpointRepository.i.saveMany(validViewpoints);
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
        .map((viewpoint) => _processViewpoint(bookId, title, author, viewpoint as String))
        .toList();

    final viewpoints = await Future.wait(viewpointFutures, eagerError: true);
    return viewpoints.where((v) => v != null).cast<BookViewpointModel>().toList();
  }

  /// 渲染模板
  ///
  /// 使用 Jinja2 语法将变量注入模板
  /// [template] 模板字符串
  /// [context] 变量上下文
  String _renderTemplate(String template, Map<String, String> context) {
    try {
      final env = Environment();
      final tmpl = env.fromString(template);
      return tmpl.render(context);
    } catch (e) {
      logger.e("[BookService服务] 模板渲染失败: $e");
      logger.e("[BookService服务] 模板: $template");
      logger.e("[BookService服务] 上下文: $context");
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

  /// 删除书籍
  Future<void> deleteBook(int bookId) async {
    BookRepository.i.remove(bookId);
  }

  /// 删除观点
  Future<bool> deleteViewpoint(int viewpointId) async {
    try {
      return BookViewpointRepository.i.remove(viewpointId);
    } catch (e, stackTrace) {
      logger.e('删除观点失败', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 保存观点
  Future<int> saveViewpoint(BookViewpointModel viewpoint) async {
    try {
      return BookViewpointRepository.i.save(viewpoint);
    } catch (e, stackTrace) {
      logger.e('保存观点失败', error: e, stackTrace: stackTrace);
      return 0;
    }
  }

  /// 刷新书籍内容：重新拉取书籍信息与观点，并替换原观点
  Future<bool> refreshBook(int bookId) async {
    final book = BookRepository.i.find(bookId);
    if (book == null) return false;

    try {
      // 重新获取书籍信息
      final promptTemplate = _pluginService.getBookInfo();
      final prompt = _renderTemplate(promptTemplate, {'title': book.title});
      final response = await _aiService.getCompletion(prompt);
      final cleanedResponse = _cleanJsonResponse(response);
      final Map<String, dynamic> bookData = jsonDecode(cleanedResponse);

      // 更新书籍基础信息
      book.author = bookData['author'] as String? ?? book.author;
      book.category = bookData['category'] as String? ?? book.category;
      book.introduction = bookData['introduction'] as String? ?? book.introduction;
      book.updatedAt = DateTime.now();
      BookRepository.i.save(book);

      // 解析观点并替换
      final List<dynamic> viewpointsData = bookData['viewpoints'] as List<dynamic>? ?? [];
      List<BookViewpointModel> viewpoints = [];
      if (viewpointsData.isNotEmpty) {
        viewpoints = await _processViewpoints(book.id, book.title, book.author, viewpointsData);
      }

      BookViewpointRepository.i.replaceForBook(book.id, viewpoints.map((e) => e.toEntity()).toList());
      return true;
    } catch (e, st) {
      logger.e('刷新书籍失败: ${book.title}', error: e, stackTrace: st);
      return false;
    }
  }
}
