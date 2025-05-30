import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';
import 'dart:convert';

import 'package:template_expressions/template_expressions.dart';

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
    //   await BookRepository.deleteAllSync();
    // }
  }

  /// 获取所有书籍
  List<BookModel> getBooks() {
    return BookRepository.getAllBooks();
  }

  /// 按分类获取书籍
  Future<List<BookModel>> getBooksByCategory(String category) async {
    return BookRepository.getBooksByCategory(category);
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
        BookRepository.saveBooks(recommendedBooks);
      }

      return recommendedBooks;
    } catch (e, stackTrace) {
      logger.e('获取分类推荐书籍失败: $category', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 获取已存在的书籍标题集合
  Set<String> _getExistingBookTitles() {
    final existingBooks = BookRepository.getAllBooks();
    return existingBooks.map((book) => book.title.toLowerCase()).toSet();
  }

  /// 从AI获取推荐书籍
  Future<List<BookModel>> _fetchRecommendedBooks(String category, Set<String> existingTitles) async {
    final promptTemplate = _pluginService.getBookRecommendByCategory();
    final prompt = _renderTemplate(promptTemplate, {'category': category});

    final response = await _aiService.getCompletion(prompt);
    final List<dynamic> booksData = jsonDecode(response);

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
      final Map<String, dynamic> viewpointData = jsonDecode(response);

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
      return jsonDecode(response);
    } catch (e, stackTrace) {
      logger.w('获取观点详情失败，正在重试...', error: e, stackTrace: stackTrace);
      final response = await _aiService.getCompletion(prompt);
      return jsonDecode(response);
    }
  }

  /// 添加书籍
  ///
  /// [title] 书名
  /// [category] 分类名（可选）
  /// 返回添加的书籍
  Future<BookModel?> addBook(String title) async {
    try {
      if (BookRepository.exists(title)) {
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
    final promptTemplate = _pluginService.getBookInfo();
    final prompt = _renderTemplate(promptTemplate, {'title': title});

    final response = await _aiService.getCompletion(prompt);
    final Map<String, dynamic> bookData = jsonDecode(response);

    final book = BookModel.create(
      title: bookData['title'] as String,
      author: bookData['author'] as String,
      category: bookData['category'] as String,
      introduction: bookData['introduction'] as String,
    );

    final bookId = BookRepository.saveBook(book);
    if (bookId <= 0) {
      logger.e('保存书籍失败: $title');
      return null;
    }

    book.id = bookId;
    return book;
  }

  /// 处理并保存书籍观点
  Future<void> _processAndSaveViewpoints(BookModel book) async {
    try {
      final promptTemplate = _pluginService.getBookInfo();
      final prompt = _renderTemplate(promptTemplate, {'title': book.title});

      final response = await _aiService.getCompletion(prompt);
      final bookData = jsonDecode(response);

      final List<dynamic> viewpointsData = bookData['viewpoints'] as List<dynamic>? ?? [];
      if (viewpointsData.isEmpty) return;

      logger.i('书籍观点: $viewpointsData');
      final validViewpoints = await _processViewpoints(book.id, book.title, book.author, viewpointsData);

      if (validViewpoints.isNotEmpty) {
        await BookRepository.saveViewpoints(validViewpoints);
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
    final List<Future<BookViewpointModel?>> viewpointFutures =
        viewpointsData
            .take(20) // 限制最多处理20个观点
            .map((viewpoint) => _processViewpoint(bookId, title, author, viewpoint as String))
            .toList();

    final viewpoints = await Future.wait(viewpointFutures, eagerError: true);
    return viewpoints.where((v) => v != null).cast<BookViewpointModel>().toList();
  }

  /// 渲染模板
  ///
  /// 使用Mustache语法将变量注入模板
  /// [template] 模板字符串
  /// [context] 变量上下文
  String _renderTemplate(String template, Map<String, String> context) {
    try {
      return Template(syntax: [MustacheExpressionSyntax()], value: template).process(context: context);
    } catch (e) {
      logger.e("[BookService服务] 模板渲染失败: $e");
      logger.e("[BookService服务] 模板: $template");
      logger.e("[BookService服务] 上下文: $context");
      return template;
    }
  }

  /// 删除书籍
  Future<void> deleteBook(int bookId) async {
    await BookRepository.deleteBook(bookId);
  }

  /// 删除观点
  Future<bool> deleteViewpoint(int viewpointId) async {
    try {
      return BookRepository.deleteViewpoint(viewpointId);
    } catch (e, stackTrace) {
      logger.e('删除观点失败', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 保存观点
  Future<int> saveViewpoint(BookViewpointModel viewpoint) async {
    try {
      return BookRepository.saveViewpoint(viewpoint);
    } catch (e, stackTrace) {
      logger.e('保存观点失败', error: e, stackTrace: stackTrace);
      return 0;
    }
  }
}
