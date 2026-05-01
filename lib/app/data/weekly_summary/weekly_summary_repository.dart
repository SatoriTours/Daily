import 'package:daily_satori/app/data/weekly_summary/weekly_summary_model.dart';
import 'package:daily_satori/app/objectbox/weekly_summary.dart';
import 'package:daily_satori/app/data/base/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 周报仓储类
///
/// 使用单例模式提供周报数据访问功能
class WeeklySummaryRepository
    extends BaseRepository<WeeklySummary, WeeklySummaryModel> {
  // 私有构造函数
  WeeklySummaryRepository._();

  // 单例实例
  static final i = WeeklySummaryRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  WeeklySummaryModel toModel(WeeklySummary entity) {
    return WeeklySummaryModel(entity);
  }

  // ==================== 特定业务方法 ====================

  /// 根据周开始日期查找周报
  WeeklySummaryModel? findByWeekStartDate(DateTime weekStartDate) {
    // 规范化日期（去除时分秒）
    final normalizedDate = DateTime(
      weekStartDate.year,
      weekStartDate.month,
      weekStartDate.day,
    );
    final query = box
        .query(
          WeeklySummary_.weekStartDate.equals(
            normalizedDate.millisecondsSinceEpoch,
          ),
        )
        .build();
    final result = executeQueryFirst(query);
    return result != null ? toModel(result) : null;
  }

  /// 查找所有已完成的周报，按周开始日期倒序
  List<WeeklySummaryModel> findAllCompleted() {
    final query = box
        .query(WeeklySummary_.status.equals('completed'))
        .order(WeeklySummary_.weekStartDate, flags: Order.descending)
        .build();
    return executeQueryModels(query);
  }

  /// 查找所有周报，按周开始日期倒序
  List<WeeklySummaryModel> findAllOrdered() {
    final query = box
        .query()
        .order(WeeklySummary_.weekStartDate, flags: Order.descending)
        .build();
    return executeQueryModels(query);
  }

  /// 查找最近N周的周报
  List<WeeklySummaryModel> findRecent(int count) {
    final query =
        box
            .query(WeeklySummary_.status.equals('completed'))
            .order(WeeklySummary_.weekStartDate, flags: Order.descending)
            .build()
          ..limit = count;
    return executeQueryModels(query);
  }

  /// 检查某周的周报是否存在
  bool existsForWeek(DateTime weekStartDate) {
    return findByWeekStartDate(weekStartDate) != null;
  }

  /// 获取或创建某周的周报
  WeeklySummaryModel getOrCreate(DateTime weekStartDate, DateTime weekEndDate) {
    final existing = findByWeekStartDate(weekStartDate);
    if (existing != null) {
      return existing;
    }

    // 创建新的周报
    final newSummary = WeeklySummaryModel.create(
      weekStartDate: weekStartDate,
      weekEndDate: weekEndDate,
      status: 'pending',
    );
    save(newSummary);
    return newSummary;
  }

  /// 更新周报内容
  void updateContent(
    int id,
    String content,
    int articleCount,
    int diaryCount,
    String? articleIds,
    String? diaryIds, {
    String? viewpointIds,
    int viewpointCount = 0,
    String? appIdeas,
  }) {
    final model = find(id);
    if (model != null) {
      model.content = content;
      model.articleCount = articleCount;
      model.diaryCount = diaryCount;
      model.articleIds = articleIds;
      model.diaryIds = diaryIds;
      model.viewpointIds = viewpointIds;
      model.viewpointCount = viewpointCount;
      model.appIdeas = appIdeas;
      model.status = WeeklySummaryStatus.completed;
      model.entity.updatedAt = DateTime.now();
      save(model);
    }
  }

  /// 更新周报状态
  void updateStatus(int id, WeeklySummaryStatus status) {
    final model = find(id);
    if (model != null) {
      model.status = status;
      model.entity.updatedAt = DateTime.now();
      save(model);
    }
  }
}
