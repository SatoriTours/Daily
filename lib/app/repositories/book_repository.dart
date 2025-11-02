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
    return BookModel.fromEntity(entity);
  }

  // ============ Book 业务查询方法 ============

  /// 根据类别获取书籍
  List<Book> getBooksByCategory(String category) {
    final query = box.query(Book_.category.equals(category)).build();
    final result = query.find();
    query.close();
    return result;
  }

  /// 根据标题搜索书籍
  List<Book> searchByTitle(String title) {
    final query = box.query(Book_.title.contains(title, caseSensitive: false)).build();
    final result = query.find();
    query.close();
    return result;
  }

  /// 根据作者搜索书籍
  List<Book> searchByAuthor(String author) {
    final query = box.query(Book_.author.contains(author, caseSensitive: false)).build();
    final result = query.find();
    query.close();
    return result;
  }

  // ============ 书籍关联的观点查询 ============

  /// 获取本书的所有观点
  List<BookViewpoint> getViewpoints(int bookId) {
    return BookViewpointRepository.i.getByBookIds([bookId]);
  }

  /// 替换书籍的所有观点
  void replaceViewpoints(int bookId, List<BookViewpoint> newViewpoints) {
    BookViewpointRepository.i.replaceForBook(bookId, newViewpoints);
  }
}
