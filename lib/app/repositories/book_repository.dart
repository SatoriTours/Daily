import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/objectbox/book.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/repositories/book_viewpoint_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 书籍存储库
///
/// 继承 `BaseRepository<Book, BookModel>` 获取Book的通用CRUD功能
/// 使用单例模式，通过 BookRepository.i 访问
class BookRepository extends BaseRepository<Book, BookModel> {
  // 私有构造函数
  BookRepository._();

  // 单例实例
  static final i = BookRepository._();

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
    final query = box.query(Book_.title.contains(title, caseSensitive: false)).build();
    return executeQuery(query);
  }

  /// 根据作者查找书籍
  List<Book> findByAuthor(String author) {
    final query = box.query(Book_.author.contains(author, caseSensitive: false)).build();
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
}
