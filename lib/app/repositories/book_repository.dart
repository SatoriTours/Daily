import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 书籍存储库
///
/// 负责书籍相关数据的存储和检索
class BookRepository {
  /// 私有构造函数
  BookRepository._();

  /// 获取Book的Box
  static Box<Book> get _box => ObjectboxService.i.box<Book>();

  /// 获取BookViewpoint的Box
  static Box<BookViewpoint> get _viewpointBox => ObjectboxService.i.box<BookViewpoint>();

  /// 检查书籍是否存在
  static bool exists(String title) {
    return _box.query(Book_.title.equals(title)).build().findFirst() != null;
  }

  /// 通过ID获取书籍
  static BookModel? getBookById(int id) {
    final book = _box.query(Book_.id.equals(id)).build().findFirst();
    return book != null ? BookModel(book) : null;
  }

  /// 获取书籍列表
  static List<BookModel> getBooks() {
    final books = _box.getAll();
    return books.map((entity) => BookModel(entity)).toList();
  }

  /// 按分类获取书籍
  static Future<List<BookModel>> getBooksByCategory(String category) async {
    try {
      final query = _box.query(Book_.category.equals(category)).build();
      final books = query.find();
      query.close();
      return books.map((entity) => BookModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('按分类获取书籍失败: $category', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 保存书籍
  static Future<int> saveBook(BookModel book) async {
    try {
      final entity = book.toEntity();
      return _box.put(entity);
    } catch (e, stackTrace) {
      logger.e('保存书籍失败: ${book.title}', error: e, stackTrace: stackTrace);
      return 0;
    }
  }

  /// 批量保存书籍
  static Future<List<int>> saveBooks(List<BookModel> books) async {
    try {
      final entities = books.map((book) => book.toEntity()).toList();
      return _box.putMany(entities);
    } catch (e, stackTrace) {
      logger.e('批量保存书籍失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 删除书籍
  static Future<void> deleteBook(int id) async {
    _box.removeAsync(id);
    _viewpointBox.query(BookViewpoint_.bookId.equals(id)).build().remove();
  }

  /// 获取所有书籍观点
  static Future<List<BookViewpointModel>> getAllViewpoints() async {
    try {
      final viewpoints = _viewpointBox.getAll();
      return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('获取所有书籍观点失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 根据书籍ID列表获取观点
  static Future<List<BookViewpointModel>> getViewpointsByBookIds(List<int> bookIds) async {
    try {
      if (bookIds.isEmpty) {
        return [];
      }

      final query = _viewpointBox.query(BookViewpoint_.bookId.oneOf(bookIds)).build();
      final viewpoints = query.find();
      query.close();
      return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('根据书籍ID列表获取观点失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 获取书籍观点列表
  static Future<List<BookViewpointModel>> getViewpoints(int bookId) async {
    try {
      if (bookId < 0) {
        // 获取所有观点，用于查找特定ID
        final viewpoints = _viewpointBox.getAll();
        return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
      }

      final query = _viewpointBox.query(BookViewpoint_.bookId.equals(bookId)).build();
      final viewpoints = query.find();
      query.close();
      return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('获取书籍观点列表失败: $bookId', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 保存书籍观点
  static Future<int> saveViewpoint(BookViewpointModel viewpoint) async {
    try {
      final entity = viewpoint.toEntity();
      return _viewpointBox.put(entity);
    } catch (e, stackTrace) {
      logger.e('保存书籍观点失败: ${viewpoint.title}', error: e, stackTrace: stackTrace);
      return 0;
    }
  }

  /// 批量保存书籍观点
  static Future<List<int>> saveViewpoints(List<BookViewpointModel> viewpoints) async {
    try {
      final entities = viewpoints.map((viewpoint) => viewpoint.toEntity()).toList();
      return _viewpointBox.putMany(entities);
    } catch (e, stackTrace) {
      logger.e('批量保存书籍观点失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 删除书籍观点
  static Future<bool> deleteViewpoint(int id) async {
    try {
      return _viewpointBox.remove(id);
    } catch (e, stackTrace) {
      logger.e('删除书籍观点失败: $id', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 删除所有书籍
  static Future<void> deleteAllSync() async {
    await _box.removeAllAsync();
    await _viewpointBox.removeAllAsync();
  }
}
