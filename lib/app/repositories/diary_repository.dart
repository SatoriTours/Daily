import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/objectbox/diary.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 日记仓库,负责管理日记的存储和检索操作
/// 继承 BaseRepository 获取通用 CRUD 功能
class DiaryRepository extends BaseRepository<Diary> {
  // 私有构造函数
  DiaryRepository._();

  // 单例
  static final DiaryRepository instance = DiaryRepository._();

  // 兼容旧的 i 访问方式
  static DiaryRepository get i => instance;

  // 获取Box实例
  @override
  Box<Diary> get box => ObjectboxService.i.box<Diary>();

  // 每页日记数量
  @override
  int get pageSize => 10;

  /// 保存日记Model
  int saveModel(DiaryModel diary) {
    return save(diary.toEntity());
  }

  /// 删除日记(旧方法名兼容)
  bool delete(int id) {
    return remove(id);
  }

  /// 获取所有日记,按创建时间倒序排列
  List<DiaryModel> getAll() {
    final entities = box.query(Diary_.id.notEquals(0)).order(Diary_.createdAt, flags: Order.descending).build().find();
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 分页获取所有日记,按创建时间倒序排列
  List<DiaryModel> getAllPaginated(int page) {
    final condition = Diary_.id.notEquals(0);
    final diaries = findByConditionPaginated(
      condition: condition,
      page: page,
      orderBy: Diary_.createdAt,
      descending: true,
    );
    return diaries.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 获取日记总数(旧方法名兼容)
  int getTotalCount() {
    return count();
  }

  /// 获取总页数(旧方法名兼容)
  int getTotalPages() {
    return totalPages();
  }

  /// 根据ID获取单个日记
  DiaryModel? getById(int id) {
    final entity = find(id);
    if (entity == null) return null;
    return DiaryModel.fromEntity(entity);
  }

  /// 按日期获取日记,返回指定日期的所有日记
  List<DiaryModel> getByDate(DateTime date) {
    final entities = findByDate(property: Diary_.createdAt, date: date);
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 按标签搜索日记
  List<DiaryModel> searchByTag(String tag) {
    final entities = searchByString(property: Diary_.tags, searchText: tag);
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 全文搜索日记内容
  List<DiaryModel> searchByContent(String keyword) {
    final entities = searchByString(property: Diary_.content, searchText: keyword);
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 分页全文搜索日记内容
  List<DiaryModel> searchByContentPaginated(String keyword, int page) {
    final condition = Diary_.content.contains(keyword);
    final diaries = findByConditionPaginated(
      condition: condition,
      page: page,
      orderBy: Diary_.createdAt,
      descending: true,
    );
    return diaries.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 获取搜索结果的总数
  int getSearchCount(String keyword) {
    final condition = Diary_.content.contains(keyword);
    return countByCondition(condition);
  }

  /// 获取搜索结果的总页数
  int getSearchTotalPages(String keyword) {
    final totalItems = getSearchCount(keyword);
    return (totalItems / pageSize).ceil();
  }

  /// 获取按月分组的日记统计
  Map<String, int> getMonthlyStats() {
    final diaries = getAll();
    final stats = <String, int>{};

    for (final diary in diaries) {
      final dateKey = '${diary.createdAt.year}-${diary.createdAt.month.toString().padLeft(2, '0')}';
      stats[dateKey] = (stats[dateKey] ?? 0) + 1;
    }

    return stats;
  }
}
