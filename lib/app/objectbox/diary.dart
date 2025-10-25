import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

@Entity()
class Diary implements BaseEntity {
  @override
  @Id()
  int id = 0;
  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;
  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  String content;
  String? tags;
  String? mood;
  String? images;

  Diary({
    this.id = 0,
    required this.content,
    DateTime? createdAt,
    DateTime? updatedAt,
    this.tags,
    this.mood,
    this.images,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
