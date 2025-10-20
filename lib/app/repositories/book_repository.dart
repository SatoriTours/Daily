import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 书籍存储库
///
/// 继承 `BaseRepository<Book>` 获取Book的通用CRUD功能
/// 同时管理BookViewpoint实体
/// 使用单例模式,通过 BookRepository.instance 访问
class BookRepository extends BaseRepository<Book> {
  // 私有构造函数
  BookRepository._();

  // 单例实例
  static final instance = BookRepository._();

  @override
  Box<Book> get box => ObjectboxService.i.box<Book>();

  @override
  int get pageSize => 20;

  // BookViewpoint box getter
  Box<BookViewpoint> get _viewpointBox => ObjectboxService.i.box<BookViewpoint>();

  // ============ Book CRUD 方法 ============

  /// 获取所有书籍
  List<Book> getAllBooks() {
    return box.getAll();
  }

  /// 根据ID获取书籍
  Book? getBookById(int id) {
    return box.get(id);
  }

  /// 根据类别获取书籍
  List<Book> getBooksByCategory(String category) {
    final query = box.query(Book_.category.equals(category)).build();
    try {
      return query.find();
    } finally {
      query.close();
    }
  }

  /// 保存书籍
  int saveBook(Book book) {
    return box.put(book);
  }

  /// 更新书籍
  int updateBook(Book book) {
    return box.put(book);
  }

  /// 批量保存书籍
  List<int> saveBooks(List<Book> books) {
    return box.putMany(books);
  }

  /// 删除书籍
  bool deleteBook(int id) {
    return box.remove(id);
  }

  /// 删除所有书籍
  int deleteAllSync() {
    return box.removeAll();
  }

  // ============ BookViewpoint CRUD 方法 ============

  /// 获取所有视角
  List<BookViewpoint> getAllViewpoints() {
    return _viewpointBox.getAll();
  }

  /// 获取所有视角(异步)
  Future<List<BookViewpoint>> getAllViewpointsAsync() async {
    return _viewpointBox.getAll();
  }

  /// 根据书籍ID列表获取视角
  List<BookViewpoint> getViewpointsByBookIds(List<int> bookIds) {
    final query = _viewpointBox.query(BookViewpoint_.bookId.oneOf(bookIds)).build();
    try {
      return query.find();
    } finally {
      query.close();
    }
  }

  /// 根据书籍ID列表获取视角(异步)
  Future<List<BookViewpoint>> getViewpointsByBookIdsAsync(List<int> bookIds) async {
    return getViewpointsByBookIds(bookIds);
  }

  /// 保存视角
  int saveViewpoint(BookViewpoint viewpoint) {
    return _viewpointBox.put(viewpoint);
  }

  /// 批量保存视角
  List<int> saveViewpoints(List<BookViewpoint> viewpoints) {
    return _viewpointBox.putMany(viewpoints);
  }

  /// 删除视角
  bool deleteViewpoint(int id) {
    return _viewpointBox.remove(id);
  }

  /// 根据ID获取视角
  BookViewpoint? getViewpointById(int id) {
    return _viewpointBox.get(id);
  }

  /// 替换书籍的所有视角
  ///
  /// 删除旧视角并保存新视角
  void replaceViewpointsForBook(int bookId, List<BookViewpoint> newViewpoints) {
    // 删除该书籍的所有旧视角
    final oldViewpoints = getViewpointsByBookIds([bookId]);
    for (var viewpoint in oldViewpoints) {
      deleteViewpoint(viewpoint.id);
    }

    // 保存新视角
    if (newViewpoints.isNotEmpty) {
      saveViewpoints(newViewpoints);
    }
  }

  // ============ 查询辅助方法 ============

  /// 检查书籍是否存在
  bool exists(int id) {
    return box.get(id) != null;
  }

  /// 获取书籍数量
  int getBookCount() {
    return box.count();
  }

  /// 获取视角数量
  int getViewpointCount() {
    return _viewpointBox.count();
  }

  /// 根据标题搜索书籍
  List<Book> searchByTitle(String title) {
    final query = box.query(Book_.title.contains(title, caseSensitive: false)).build();
    try {
      return query.find();
    } finally {
      query.close();
    }
  }

  /// 根据作者搜索书籍
  List<Book> searchByAuthor(String author) {
    final query = box.query(Book_.author.contains(author, caseSensitive: false)).build();
    try {
      return query.find();
    } finally {
      query.close();
    }
  }

  // ============ 返回Model的方法 ============

  /// 获取所有书籍(返回Model)
  List<BookModel> getAllBooksModel() {
    return getAllBooks().map((e) => BookModel(e)).toList();
  }

  /// 根据ID获取书籍(返回Model)
  BookModel? getBookByIdModel(int id) {
    final book = getBookById(id);
    return book != null ? BookModel(book) : null;
  }

  /// 根据类别获取书籍(返回Model)
  List<BookModel> getBooksByCategoryModel(String category) {
    return getBooksByCategory(category).map((e) => BookModel(e)).toList();
  }

  /// 获取所有视角(返回Model)
  List<BookViewpointModel> getAllViewpointsModel() {
    return getAllViewpoints().map((e) => BookViewpointModel(e)).toList();
  }

  /// 根据书籍ID列表获取视角(返回Model)
  List<BookViewpointModel> getViewpointsByBookIdsModel(List<int> bookIds) {
    return getViewpointsByBookIds(bookIds).map((e) => BookViewpointModel(e)).toList();
  }

  /// 根据ID获取视角(返回Model)
  BookViewpointModel? getViewpointByIdModel(int id) {
    final viewpoint = getViewpointById(id);
    return viewpoint != null ? BookViewpointModel(viewpoint) : null;
  }
}
