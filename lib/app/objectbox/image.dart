import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';
import 'package:daily_satori/app/objectbox/article.dart';

@Entity()
class Image implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  String? url;
  String? path;

  final article = ToOne<Article>();

  Image({this.id = 0, this.url, this.path, DateTime? createdAt, DateTime? updatedAt}) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
