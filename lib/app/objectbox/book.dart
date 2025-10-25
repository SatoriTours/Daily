import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

@Entity()
class Book implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  String title;
  String author;
  String category;
  String coverImage;
  String introduction;

  /// 是否有更新
  bool hasUpdate;

  // 保持向后兼容性的别名
  DateTime get createAt => createdAt;
  set createAt(DateTime value) => createdAt = value;

  DateTime get updateAt => updatedAt;
  set updateAt(DateTime value) => updatedAt = value;

  Book({
    this.id = 0,
    required this.title,
    required this.author,
    required this.category,
    this.coverImage = '',
    this.introduction = '',
    this.hasUpdate = false,
    DateTime? createAt,
    DateTime? updateAt,
  }) {
    createdAt = createAt ?? DateTime.now();
    updatedAt = updateAt ?? DateTime.now();
  }
}
