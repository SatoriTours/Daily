import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/objectbox/diary.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 日记仓库，负责管理日记的存储和检索操作
class DiaryRepository {
  DiaryRepository._();
  static final DiaryRepository _instance = DiaryRepository._();
  static DiaryRepository get i => _instance;

  /// 获取日记 Box
  static Box<Diary> get _box => ObjectboxService.i.box<Diary>();

  /// 保存日记
  int save(DiaryModel diary) {
    return _box.put(diary.toEntity());
  }

  /// 删除日记
  bool delete(int id) {
    return _box.remove(id);
  }

  /// 获取所有日记，按创建时间倒序排列
  List<DiaryModel> getAll() {
    final entities = _box.query(Diary_.id.notEquals(0)).order(Diary_.createdAt, flags: Order.descending).build().find();
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 根据ID获取单个日记
  DiaryModel? getById(int id) {
    final entity = _box.get(id);
    if (entity == null) return null;
    return DiaryModel.fromEntity(entity);
  }

  /// 按日期获取日记，返回指定日期的所有日记
  List<DiaryModel> getByDate(DateTime date) {
    final startOfDay = DateTime(date.year, date.month, date.day);
    final endOfDay = DateTime(date.year, date.month, date.day, 23, 59, 59, 999);

    final entities =
        _box
            .query(Diary_.createdAt.between(startOfDay.millisecondsSinceEpoch, endOfDay.millisecondsSinceEpoch))
            .order(Diary_.createdAt, flags: Order.descending)
            .build()
            .find();
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 按标签搜索日记
  List<DiaryModel> searchByTag(String tag) {
    // 模糊搜索，查找标签字段中包含指定标签的日记
    final entities =
        _box.query(Diary_.tags.contains(tag)).order(Diary_.createdAt, flags: Order.descending).build().find();
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
  }

  /// 全文搜索日记内容
  List<DiaryModel> searchByContent(String keyword) {
    final entities =
        _box.query(Diary_.content.contains(keyword)).order(Diary_.createdAt, flags: Order.descending).build().find();
    return entities.map((entity) => DiaryModel.fromEntity(entity)).toList();
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
