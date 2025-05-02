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

  Future<void> init() async {
    // if (!AppInfoUtils.isProduction) {
    //   await BookRepository.deleteAllSync();
    // }
  }

  final AiService _aiService = AiService.i;
  final PluginService _pluginService = PluginService.i;

  /// 获取所有书籍
  Future<List<BookModel>> getBooks() async {
    return BookRepository.getBooks();
  }

  /// 按分类获取书籍
  Future<List<BookModel>> getBooksByCategory(String category) async {
    return BookRepository.getBooksByCategory(category);
  }

  /// 获取所有分类
  Future<List<BookCategoryModel>> getCategories() async {
    return BookRepository.getCategories();
  }

  /// 保存分类
  Future<int> saveCategory(BookCategoryModel category) async {
    return BookRepository.saveCategory(category);
  }

  /// 通过分类获取推荐书籍
  ///
  /// [category] 书籍分类
  /// 返回该分类下的推荐书籍列表
  Future<List<BookModel>> getRecommendedBooksByCategory(String category) async {
    try {
      // 获取现有书籍，用于过滤已添加的书籍
      final existingBooks = await BookRepository.getBooks();
      final existingTitles = existingBooks.map((book) => book.title.toLowerCase()).toSet();

      // 使用AI获取该分类下最经典的10本书
      final promptTemplate = _pluginService.getBookRecommendByCategory();
      final prompt = _renderTemplate(promptTemplate, {'category': category});

      final response = await _aiService.getCompletion(prompt);
      final List<dynamic> booksData = jsonDecode(response);

      // 将推荐的书籍转换为Book对象
      final List<BookModel> recommendedBooks = [];
      for (final bookData in booksData) {
        final title = bookData['title'] as String;
        // 过滤已存在的书籍
        if (!existingTitles.contains(title.toLowerCase())) {
          recommendedBooks.add(
            BookModel.create(
              title: title,
              author: bookData['author'] as String,
              category: category,
              introduction: bookData['introduction'] as String,
            ),
          );
        }
      }

      // 保存推荐的书籍
      if (recommendedBooks.isNotEmpty) {
        await BookRepository.saveBooks(recommendedBooks);
      }

      return recommendedBooks;
    } catch (e, stackTrace) {
      logger.e('获取分类推荐书籍失败: $category', error: e, stackTrace: stackTrace);
      return [];
    }
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
      // 获取观点的详细解读和案例
      final promptTemplate = _pluginService.getBookViewpoint();
      final prompt = _renderTemplate(promptTemplate, {'title': title, 'author': author, 'viewpoint': viewpoint});

      final response = await _aiService.getCompletion(prompt);
      final Map<String, dynamic> viewpointDetail = jsonDecode(response);

      return BookViewpointModel.create(
        bookId: bookId,
        title: viewpoint,
        content: viewpointDetail['content'] as String,
        example: viewpointDetail['example'] as String,
      );
    } catch (e, stackTrace) {
      logger.e('处理观点详情失败: $title - $viewpoint', error: e, stackTrace: stackTrace);
      // 发生错误时，使用原始数据创建观点
      return null;
    }
  }

  /// 添加书籍
  ///
  /// [title] 书名
  /// [category] 分类名（可选）
  /// 返回添加的书籍
  Future<BookModel?> addBook(String title, {String category = ''}) async {
    try {
      // 检查书籍是否已存在
      final existingBooks = await BookRepository.getBooks();
      BookModel? existingBook;
      try {
        existingBook = existingBooks.firstWhere((book) => book.title.toLowerCase() == title.toLowerCase());
      } catch (_) {
        // 没有找到匹配的书籍
        existingBook = null;
      }

      if (existingBook != null) {
        return existingBook;
      }

      // 使用AI获取书籍详细信息和核心观点
      final promptTemplate = _pluginService.getBookInfo();
      final prompt = _renderTemplate(promptTemplate, {
        'title': title,
        'category': category.isNotEmpty ? category : '适合的分类',
      });

      final response = await _aiService.getCompletion(prompt);
      final Map<String, dynamic> bookData = jsonDecode(response);

      // 创建新书籍
      final book = BookModel.create(
        title: bookData['title'] as String,
        author: bookData['author'] as String,
        category: bookData['category'] as String,
        introduction: bookData['introduction'] as String,
      );

      // 保存书籍
      final bookId = await BookRepository.saveBook(book);
      if (bookId <= 0) {
        return null;
      }

      book.id = bookId;

      // 获取并处理书籍的核心观点
      final List<dynamic> viewpointsData = bookData['viewpoints'] as List<dynamic>? ?? [];

      if (viewpointsData.isNotEmpty) {
        // 并发处理每个观点的详细信息
        final List<Future<BookViewpointModel?>> viewpointFutures =
            viewpointsData
                .take(20) // 限制最多处理20个观点
                .map((viewpoint) => _processViewpoint(bookId, book.title, book.author, viewpoint as String))
                .toList();

        final List<BookViewpointModel?> viewpoints = await Future.wait(viewpointFutures);

        // 过滤掉为空的观点
        final List<BookViewpointModel> validViewpoints =
            viewpoints.where((viewpoint) => viewpoint != null).cast<BookViewpointModel>().toList();

        // 保存处理后的观点
        if (viewpoints.isNotEmpty) {
          await BookRepository.saveViewpoints(validViewpoints);
        }
      }

      return book;
    } catch (e, stackTrace) {
      logger.e('添加书籍失败: $title, ${stackTrace.toString()}', error: e, stackTrace: stackTrace);
      return null;
    }
  }

  /// 更新书籍观点的感悟
  ///
  /// [viewpointId] 观点ID
  /// [feeling] 个人感悟
  /// 返回是否更新成功
  Future<bool> updateViewpointFeeling(int viewpointId, String feeling) async {
    try {
      // 获取观点
      final viewpoints = await BookRepository.getViewpoints(-1);
      BookViewpointModel? viewpoint;
      try {
        viewpoint = viewpoints.firstWhere((vp) => vp.id == viewpointId);
      } catch (_) {
        // 没有找到匹配的观点
        viewpoint = null;
      }

      if (viewpoint == null) {
        return false;
      }

      // 更新感悟
      viewpoint.feeling = feeling;
      viewpoint.updateAt = DateTime.now();

      // 保存更新
      final result = await BookRepository.saveViewpoint(viewpoint);
      return result > 0;
    } catch (e, stackTrace) {
      logger.e('更新观点感悟失败: $viewpointId', error: e, stackTrace: stackTrace);
      return false;
    }
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
  Future<bool> deleteBook(int bookId) async {
    try {
      return await BookRepository.deleteBook(bookId);
    } catch (e, stackTrace) {
      logger.e('删除书籍失败', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 删除观点
  Future<bool> deleteViewpoint(int viewpointId) async {
    try {
      return await BookRepository.deleteViewpoint(viewpointId);
    } catch (e, stackTrace) {
      logger.e('删除观点失败', error: e, stackTrace: stackTrace);
      return false;
    }
  }
}
