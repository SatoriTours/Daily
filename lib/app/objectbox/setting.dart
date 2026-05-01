import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

@Entity()
class Setting implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  @Unique()
  String? key;
  String? value;

  Setting({
    this.id = 0,
    this.key,
    this.value,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
