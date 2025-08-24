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
  static final Box<Book> _bookBox = ObjectboxService.i.box<Book>();

  /// 获取BookViewpoint的Box
  static final Box<BookViewpoint> _viewpointBox = ObjectboxService.i.box<BookViewpoint>();

  // ===================== 书籍基本操作 =====================

  /// 检查书籍是否存在
  static bool exists(String title) {
    return _bookBox.query(Book_.title.equals(title)).build().findFirst() != null;
  }

  /// 通过ID获取书籍
  static BookModel? getBookById(int id) {
    final book = _bookBox.query(Book_.id.equals(id)).build().findFirst();
    return book != null ? BookModel(book) : null;
  }

  /// 获取书籍列表
  static List<BookModel> getAllBooks() {
    final books = _bookBox.getAll();
    return books.map((entity) => BookModel(entity)).toList();
  }

  /// 按分类获取书籍
  static List<BookModel> getBooksByCategory(String category) {
    final query = _bookBox.query(Book_.category.equals(category)).build();
    final books = query.find();
    query.close();
    return books.map((entity) => BookModel(entity)).toList();
  }

  /// 保存书籍
  static int saveBook(BookModel book) {
    final entity = book.toEntity();
    return _bookBox.put(entity);
  }

  /// 更新书籍
  static int updateBook(BookModel book) {
    final entity = book.toEntity();
    return _bookBox.put(entity);
  }

  /// 批量保存书籍
  static List<int> saveBooks(List<BookModel> books) {
    final entities = books.map((book) => book.toEntity()).toList();
    return _bookBox.putMany(entities);
  }

  /// 删除书籍
  static Future<void> deleteBook(int id) async {
    await _bookBox.removeAsync(id);
    await _viewpointBox.query(BookViewpoint_.bookId.equals(id)).build().removeAsync();
  }

  /// 删除所有书籍
  static Future<void> deleteAllSync() async {
    await _bookBox.removeAllAsync();
    await _viewpointBox.removeAllAsync();
  }

  // ===================== 书籍观点操作 =====================

  /// 获取所有书籍观点
  static List<BookViewpointModel> getAllViewpoints() {
    final viewpoints = _viewpointBox.getAll();
    return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
  }

  static Future<List<BookViewpointModel>> getAllViewpointsAsync() async {
    final viewpoints = await _viewpointBox.getAllAsync();
    return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
  }

  /// 根据书籍ID列表获取观点
  static List<BookViewpointModel> getViewpointsByBookIds(List<int> bookIds) {
    if (bookIds.isEmpty) {
      return [];
    }

    final query = _viewpointBox.query(BookViewpoint_.bookId.oneOf(bookIds)).build();
    final viewpoints = query.find();
    query.close();
    return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
  }

  /// 根据书籍ID列表获取观点
  static Future<List<BookViewpointModel>> getViewpointsByBookIdsAsync(List<int> bookIds) async {
    if (bookIds.isEmpty) {
      return [];
    }

    final query = _viewpointBox.query(BookViewpoint_.bookId.oneOf(bookIds)).build();
    final viewpoints = await query.findAsync();
    query.close();
    return viewpoints.map((entity) => BookViewpointModel(entity)).toList();
  }

  /// 保存书籍观点
  static int saveViewpoint(BookViewpointModel viewpoint) {
    final entity = viewpoint.toEntity();
    return _viewpointBox.put(entity);
  }

  /// 批量保存书籍观点
  static Future<List<int>> saveViewpoints(List<BookViewpointModel> viewpoints) async {
    final entities = viewpoints.map((viewpoint) => viewpoint.toEntity()).toList();
    return await _viewpointBox.putManyAsync(entities);
  }

  /// 删除书籍观点
  static bool deleteViewpoint(int id) {
    return _viewpointBox.remove(id);
  }

  /// 用新观点替换某本书的所有观点
  static Future<void> replaceViewpointsForBook(int bookId, List<BookViewpointModel> newViewpoints) async {
    // 先删除旧的
    await _viewpointBox.query(BookViewpoint_.bookId.equals(bookId)).build().removeAsync();
    if (newViewpoints.isEmpty) return;
    // 再插入新的
    final entities = newViewpoints.map((v) => v.toEntity()).toList();
    await _viewpointBox.putManyAsync(entities);
  }
}
