import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';
import 'package:daily_satori/app/objectbox/article.dart';

@Entity()
class Tag implements BaseEntity {
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
  String? name;
  String? icon;

  @Backlink()
  final articles = ToMany<Article>();

  Tag({
    this.id = 0,
    this.name,
    this.icon,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
