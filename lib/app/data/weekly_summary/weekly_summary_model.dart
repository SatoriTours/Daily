import 'package:daily_satori/app/data/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/weekly_summary.dart';

/// 周报状态枚举
enum WeeklySummaryStatus {
  pending('pending'),
  generating('generating'),
  completed('completed'),
  failed('failed');

  final String value;
  const WeeklySummaryStatus(this.value);

  static WeeklySummaryStatus fromValue(String value) {
    return WeeklySummaryStatus.values.firstWhere((e) => e.value == value, orElse: () => WeeklySummaryStatus.pending);
  }
}

/// 周报模型类
class WeeklySummaryModel extends EntityModel<WeeklySummary> {
  WeeklySummaryModel(super.entity);

  factory WeeklySummaryModel.create({
    int id = 0,
    required DateTime weekStartDate,
    required DateTime weekEndDate,
    String content = '',
    int articleCount = 0,
    int diaryCount = 0,
    String? articleIds,
    String? diaryIds,
    String status = 'pending',
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return WeeklySummaryModel(
      WeeklySummary(
        id: id,
        weekStartDate: weekStartDate,
        weekEndDate: weekEndDate,
        content: content,
        articleCount: articleCount,
        diaryCount: diaryCount,
        articleIds: articleIds,
        diaryIds: diaryIds,
        status: status,
        createdAt: createdAt,
        updatedAt: updatedAt,
      ),
    );
  }

  // ==================== 基本属性 ====================

  DateTime get weekStartDate => entity.weekStartDate;
  set weekStartDate(DateTime value) => entity.weekStartDate = value;

  DateTime get weekEndDate => entity.weekEndDate;
  set weekEndDate(DateTime value) => entity.weekEndDate = value;

  String get content => entity.content;
  set content(String value) => entity.content = value;

  int get articleCount => entity.articleCount;
  set articleCount(int value) => entity.articleCount = value;

  int get diaryCount => entity.diaryCount;
  set diaryCount(int value) => entity.diaryCount = value;

  String? get articleIds => entity.articleIds;
  set articleIds(String? value) => entity.articleIds = value;

  String? get diaryIds => entity.diaryIds;
  set diaryIds(String? value) => entity.diaryIds = value;

  WeeklySummaryStatus get status => WeeklySummaryStatus.fromValue(entity.status);
  set status(WeeklySummaryStatus value) => entity.status = value.value;

  // ==================== 计算属性 ====================

  /// 获取关联的文章ID列表
  List<int> get articleIdList {
    if (articleIds == null || articleIds!.isEmpty) return [];
    return articleIds!.split(',').map((e) => int.tryParse(e) ?? 0).where((e) => e > 0).toList();
  }

  /// 获取关联的日记ID列表
  List<int> get diaryIdList {
    if (diaryIds == null || diaryIds!.isEmpty) return [];
    return diaryIds!.split(',').map((e) => int.tryParse(e) ?? 0).where((e) => e > 0).toList();
  }

  /// 获取周的显示标题
  String get weekTitle {
    final start = weekStartDate;
    final end = weekEndDate;
    return '${start.month}月${start.day}日 - ${end.month}月${end.day}日';
  }

  /// 获取年份和第几周
  String get weekLabel {
    final weekNumber = _getWeekNumber(weekStartDate);
    return '${weekStartDate.year}年第$weekNumber周';
  }

  /// 是否已完成
  bool get isCompleted => status == WeeklySummaryStatus.completed;

  /// 是否正在生成
  bool get isGenerating => status == WeeklySummaryStatus.generating;

  /// 是否失败
  bool get isFailed => status == WeeklySummaryStatus.failed;

  // ==================== 辅助方法 ====================

  /// 计算一年中的第几周
  int _getWeekNumber(DateTime date) {
    final firstDayOfYear = DateTime(date.year, 1, 1);
    final daysDiff = date.difference(firstDayOfYear).inDays;
    return ((daysDiff + firstDayOfYear.weekday - 1) / 7).ceil();
  }

  // ==================== 转换方法 ====================

  WeeklySummary toEntity() => entity;
}
