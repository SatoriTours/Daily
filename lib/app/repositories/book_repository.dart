import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/objectbox/book.dart' as ob;
import 'package:daily_satori/app/objectbox/book_category.dart' as ob_cat;
import 'package:daily_satori/app/objectbox/book_viewpoint.dart' as ob_vp;

/// 书籍存储库
///
/// 负责书籍相关数据的存储和检索
class BookRepository {
  // 临时存储，等待 ObjectBox 生成文件
  final Map<int, ob.Book> _booksCache = {};
  final Map<int, ob_cat.BookCategory> _categoriesCache = {};
  final Map<int, ob_vp.BookViewpoint> _viewpointsCache = {};
  int _nextBookId = 1;
  int _nextCategoryId = 1;
  int _nextViewpointId = 1;

  /// 获取书籍列表
  Future<List<BookModel>> getBooks() async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final bookBox = objectBox.store.box<Book>();
      // return bookBox.getAll();
      return _booksCache.values.map((entity) => BookModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('获取书籍列表失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 按分类获取书籍
  Future<List<BookModel>> getBooksByCategory(String category) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final bookBox = objectBox.store.box<Book>();
      // final query = bookBox.query(Book_.category.equals(category)).build();
      // return query.find();
      return _booksCache.values.where((book) => book.category == category).map((entity) => BookModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('按分类获取书籍失败: $category', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 保存书籍
  Future<int> saveBook(BookModel book) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final bookBox = objectBox.store.box<Book>();
      // return bookBox.put(book);
      final entity = book.toEntity();
      if (entity.id == 0) {
        entity.id = _nextBookId++;
      }
      _booksCache[entity.id] = entity;
      return entity.id;
    } catch (e, stackTrace) {
      logger.e('保存书籍失败: ${book.title}', error: e, stackTrace: stackTrace);
      return 0;
    }
  }

  /// 批量保存书籍
  Future<List<int>> saveBooks(List<BookModel> books) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final bookBox = objectBox.store.box<Book>();
      // return bookBox.putMany(books);
      final ids = <int>[];
      for (final book in books) {
        final id = await saveBook(book);
        ids.add(id);
      }
      return ids;
    } catch (e, stackTrace) {
      logger.e('批量保存书籍失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 删除书籍
  Future<bool> deleteBook(int id) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final bookBox = objectBox.store.box<Book>();
      // return bookBox.remove(id);
      return _booksCache.remove(id) != null;
    } catch (e, stackTrace) {
      logger.e('删除书籍失败: $id', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 获取书籍分类列表
  Future<List<BookCategoryModel>> getCategories() async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final categoryBox = objectBox.store.box<BookCategory>();
      // return categoryBox.getAll();
      return _categoriesCache.values.map((entity) => BookCategoryModel(entity)).toList();
    } catch (e, stackTrace) {
      logger.e('获取书籍分类列表失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 保存书籍分类
  Future<int> saveCategory(BookCategoryModel category) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final categoryBox = objectBox.store.box<BookCategory>();
      // return categoryBox.put(category);
      final entity = category.toEntity();
      if (entity.id == 0) {
        entity.id = _nextCategoryId++;
      }
      _categoriesCache[entity.id] = entity;
      return entity.id;
    } catch (e, stackTrace) {
      logger.e('保存书籍分类失败: ${category.name}', error: e, stackTrace: stackTrace);
      return 0;
    }
  }

  /// 删除书籍分类
  Future<bool> deleteCategory(int id) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final categoryBox = objectBox.store.box<BookCategory>();
      // return categoryBox.remove(id);
      return _categoriesCache.remove(id) != null;
    } catch (e, stackTrace) {
      logger.e('删除书籍分类失败: $id', error: e, stackTrace: stackTrace);
      return false;
    }
  }

  /// 获取书籍观点列表
  Future<List<BookViewpointModel>> getViewpoints(int bookId) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final viewpointBox = objectBox.store.box<BookViewpoint>();
      // final query = viewpointBox.query(BookViewpoint_.bookId.equals(bookId)).build();
      // return query.find();
      if (bookId < 0) {
        // 获取所有观点，用于查找特定ID
        return _viewpointsCache.values.map((entity) => BookViewpointModel(entity)).toList();
      }
      return _viewpointsCache.values
          .where((vp) => vp.bookId == bookId)
          .map((entity) => BookViewpointModel(entity))
          .toList();
    } catch (e, stackTrace) {
      logger.e('获取书籍观点列表失败: $bookId', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 保存书籍观点
  Future<int> saveViewpoint(BookViewpointModel viewpoint) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final viewpointBox = objectBox.store.box<BookViewpoint>();
      // return viewpointBox.put(viewpoint);
      final entity = viewpoint.toEntity();
      if (entity.id == 0) {
        entity.id = _nextViewpointId++;
      }
      _viewpointsCache[entity.id] = entity;
      return entity.id;
    } catch (e, stackTrace) {
      logger.e('保存书籍观点失败: ${viewpoint.title}', error: e, stackTrace: stackTrace);
      return 0;
    }
  }

  /// 批量保存书籍观点
  Future<List<int>> saveViewpoints(List<BookViewpointModel> viewpoints) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final viewpointBox = objectBox.store.box<BookViewpoint>();
      // return viewpointBox.putMany(viewpoints);
      final ids = <int>[];
      for (final viewpoint in viewpoints) {
        final id = await saveViewpoint(viewpoint);
        ids.add(id);
      }
      return ids;
    } catch (e, stackTrace) {
      logger.e('批量保存书籍观点失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }

  /// 删除书籍观点
  Future<bool> deleteViewpoint(int id) async {
    try {
      // 等待 ObjectBox 配置好后替换为实际实现
      // final viewpointBox = objectBox.store.box<BookViewpoint>();
      // return viewpointBox.remove(id);
      return _viewpointsCache.remove(id) != null;
    } catch (e, stackTrace) {
      logger.e('删除书籍观点失败: $id', error: e, stackTrace: stackTrace);
      return false;
    }
  }
}
