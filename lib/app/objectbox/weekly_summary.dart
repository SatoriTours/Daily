import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

/// 周报实体
///
/// 存储每周的总结信息，包括文章和日记的AI总结
@Entity()
class WeeklySummary implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  /// 周的开始日期（周一）
  @Property(type: PropertyType.date)
  late DateTime weekStartDate;

  /// 周的结束日期（周日）
  @Property(type: PropertyType.date)
  late DateTime weekEndDate;

  /// AI生成的总结内容（Markdown格式）
  String content;

  /// 该周的文章数量
  int articleCount;

  /// 该周的日记数量
  int diaryCount;

  /// 关联的文章ID列表（逗号分隔）
  String? articleIds;

  /// 关联的日记ID列表（逗号分隔）
  String? diaryIds;

  /// 总结状态：pending, generating, completed, failed
  String status;

  WeeklySummary({
    this.id = 0,
    required this.weekStartDate,
    required this.weekEndDate,
    this.content = '',
    this.articleCount = 0,
    this.diaryCount = 0,
    this.articleIds,
    this.diaryIds,
    this.status = 'pending',
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
