import 'package:daily_satori/app/data/diary/diary_model.dart';
import 'package:daily_satori/app/objectbox/diary.dart';
import 'package:daily_satori/app/data/base/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 日记仓库,负责管理日记的存储和检索操作
/// 继承 BaseRepository 获取通用 CRUD 功能
class DiaryRepository extends BaseRepository<Diary, DiaryModel> {
  // 私有构造函数
  DiaryRepository._();

  // 单例
  static final i = DiaryRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  DiaryModel toModel(Diary entity) {
    return DiaryModel(entity);
  }

  /// 删除日记(旧方法名兼容)
  bool delete(int id) {
    return remove(id);
  }

  /// 查找所有日记,按创建时间倒序排列
  List<DiaryModel> findAll() {
    final condition = Diary_.id.notEquals(0);
    return findByConditionPaginated(condition: condition, page: 1, orderBy: Diary_.createdAt, descending: true);
  }

  /// 分页查找所有日记,按创建时间倒序排列
  List<DiaryModel> findAllPaginated(int page) {
    final condition = Diary_.id.notEquals(0);
    return findByConditionPaginated(condition: condition, page: page, orderBy: Diary_.createdAt, descending: true);
  }

  /// 按日期查找日记,返回指定日期的所有日记
  List<DiaryModel> findByCreatedDate(DateTime date) {
    return findByDate(property: Diary_.createdAt, date: date);
  }

  /// 按标签查找日记
  List<DiaryModel> findByTag(String tag) {
    return searchByString(property: Diary_.tags, searchText: tag);
  }

  /// 全文查找日记内容和标签
  ///
  /// [keyword] 搜索关键词，会在 content 和 tags 中搜索
  List<DiaryModel> findByContent(String keyword) {
    // 同时搜索内容和标签，提高搜索命中率
    final condition = Diary_.content
        .contains(keyword, caseSensitive: false)
        .or(Diary_.tags.contains(keyword, caseSensitive: false));
    final query = box.query(condition).order(Diary_.createdAt, flags: Order.descending).build();
    return executeQuery(query).map(toModel).toList();
  }

  /// 分页全文查找日记内容和标签
  List<DiaryModel> findByContentPaginated(String keyword, int page) {
    final condition = Diary_.content
        .contains(keyword, caseSensitive: false)
        .or(Diary_.tags.contains(keyword, caseSensitive: false));
    return findByConditionPaginated(condition: condition, page: page, orderBy: Diary_.createdAt, descending: true);
  }

  /// 获取查找结果的总数
  int getSearchCount(String keyword) {
    final condition = Diary_.content.contains(keyword);
    return countByCondition(condition);
  }

  /// 获取查找结果的总页数
  int getSearchTotalPages(String keyword) {
    final totalItems = getSearchCount(keyword);
    return (totalItems / pageSize).ceil();
  }

  /// 获取按月分组的日记统计
  Map<String, int> getMonthlyStats() {
    final diaries = findAll();
    final stats = <String, int>{};

    for (final diary in diaries) {
      final dateKey = '${diary.createdAt.year}-${diary.createdAt.month.toString().padLeft(2, '0')}';
      stats[dateKey] = (stats[dateKey] ?? 0) + 1;
    }

    return stats;
  }
}
