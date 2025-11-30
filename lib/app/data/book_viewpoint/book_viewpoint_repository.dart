import 'package:daily_satori/app/data/book_viewpoint/book_viewpoint.dart';
import 'package:daily_satori/app/objectbox/book_viewpoint.dart';
import 'package:daily_satori/app/data/base/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 书籍观点存储库
///
/// 继承 `BaseRepository<BookViewpoint, BookViewpointModel>` 获取通用CRUD功能
/// 使用单例模式，通过 BookViewpointRepository.i 访问
class BookViewpointRepository extends BaseRepository<BookViewpoint, BookViewpointModel> {
  // 私有构造函数
  BookViewpointRepository._();

  // 单例实例
  static final i = BookViewpointRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  BookViewpointModel toModel(BookViewpoint entity) {
    return BookViewpointModel(entity);
  }

  // ============ BookViewpoint 业务查询方法 ============

  /// 根据书籍ID列表查找视角
  List<BookViewpoint> findByBookIds(List<int> bookIds) {
    final query = box.query(BookViewpoint_.bookId.oneOf(bookIds)).build();
    return executeQuery(query);
  }

  /// 根据书籍ID查找视角（返回Model）
  List<BookViewpointModel> findModelsByBookIds(List<int> bookIds) {
    return findByBookIds(bookIds).map((e) => toModel(e)).toList();
  }

  /// 替换书籍的所有视角
  ///
  /// 删除旧视角并保存新视角
  void replaceForBook(int bookId, List<BookViewpoint> newViewpoints) {
    // 删除该书籍的所有旧视角
    final oldViewpoints = findByBookIds([bookId]);
    if (oldViewpoints.isNotEmpty) {
      removeMany(oldViewpoints.map((e) => e.id).toList());
    }

    // 保存新视角
    if (newViewpoints.isNotEmpty) {
      final models = newViewpoints.map(toModel).toList();
      saveMany(models);
    }
  }

  /// 搜索读书笔记内容
  ///
  /// [keyword] 搜索关键词，在 title 和 content 中搜索
  /// [limit] 返回数量限制
  List<BookViewpoint> findByContent(String keyword, {int? limit}) {
    final condition = BookViewpoint_.title
        .contains(keyword, caseSensitive: false)
        .or(BookViewpoint_.content.contains(keyword, caseSensitive: false));
    final query = box.query(condition).order(BookViewpoint_.id, flags: Order.descending).build();
    if (limit != null) {
      query.limit = limit;
    }
    return executeQuery(query);
  }
}
