import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';
import 'dart:convert';
import 'package:collection/collection.dart';

import 'package:template_expressions/template_expressions.dart';

/// 书籍服务
///
/// 负责处理与书籍相关的业务逻辑，包括AI推荐、书籍信息获取等
class BookService {
  static final BookService _instance = BookService._();
  static BookService get i => _instance;

  BookService._();

  final AiService _aiService = AiService.i;
  final BookRepository _bookRepository = BookRepository();
  final PluginService _pluginService = PluginService.i;

  /// 获取所有书籍
  Future<List<BookModel>> getBooks() async {
    return _bookRepository.getBooks();
  }

  /// 按分类获取书籍
  Future<List<BookModel>> getBooksByCategory(String category) async {
    return _bookRepository.getBooksByCategory(category);
  }

  /// 获取所有分类
  Future<List<BookCategoryModel>> getCategories() async {
    return _bookRepository.getCategories();
  }

  /// 保存分类
  Future<int> saveCategory(BookCategoryModel category) async {
    return _bookRepository.saveCategory(category);
  }

  /// 通过分类获取推荐书籍
  ///
  /// [category] 书籍分类
  /// 返回该分类下的推荐书籍列表
  Future<List<BookModel>> getRecommendedBooksByCategory(String category) async {
    try {
      // 获取现有书籍，用于过滤已添加的书籍
      final existingBooks = await _bookRepository.getBooks();
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
        await _bookRepository.saveBooks(recommendedBooks);
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
  Future<List<BookViewpointModel>> getBookViewpoints(BookModel book) async {
    try {
      // 检查是否已有观点数据
      final existingViewpoints = await _bookRepository.getViewpoints(book.id);
      if (existingViewpoints.isNotEmpty) {
        return existingViewpoints;
      }

      // 使用AI获取书籍的关键观点
      final promptTemplate = _pluginService.getBookViewpoints();
      final prompt = _renderTemplate(promptTemplate, {'title': book.title, 'author': book.author});

      final response = await _aiService.getCompletion(prompt);
      final Map<String, dynamic> jsonData = jsonDecode(response);
      final List<dynamic> viewpointsData = jsonData['viewpoints'] as List<dynamic>;

      // 将观点数据转换为BookViewpoint对象
      final List<BookViewpointModel> viewpoints =
          viewpointsData.map((viewpointData) {
            return BookViewpointModel.create(
              bookId: book.id,
              title: viewpointData['title'] as String,
              content: viewpointData['content'] as String,
              example: viewpointData['example'] as String,
            );
          }).toList();

      // 保存观点数据
      if (viewpoints.isNotEmpty) {
        await _bookRepository.saveViewpoints(viewpoints);
      }

      return viewpoints;
    } catch (e, stackTrace) {
      logger.e('获取书籍观点失败: ${book.title} ${stackTrace.toString()}', error: e, stackTrace: stackTrace);
      return [];
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
      final existingBooks = await _bookRepository.getBooks();
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

      // 使用AI获取书籍详细信息
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
      final bookId = await _bookRepository.saveBook(book);
      if (bookId > 0) {
        book.id = bookId;
        // 获取书籍观点（在后台进行，不等待结果）
        getBookViewpoints(book);
        return book;
      }

      return null;
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
      final viewpoints = await _bookRepository.getViewpoints(-1);
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
      final result = await _bookRepository.saveViewpoint(viewpoint);
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
      return await _bookRepository.deleteBook(bookId);
    } catch (e, stackTrace) {
      logger.e('删除书籍失败', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 删除观点
  Future<bool> deleteViewpoint(int viewpointId) async {
    try {
      return await _bookRepository.deleteViewpoint(viewpointId);
    } catch (e, stackTrace) {
      logger.e('删除观点失败', error: e, stackTrace: stackTrace);
      return false;
    }
  }
}
