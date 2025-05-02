import 'package:objectbox/objectbox.dart';
import 'package:json_annotation/json_annotation.dart';

@Entity()
@JsonSerializable()
class BookCategory {
  @Id()
  int id = 0;

  /// 分类名称
  String name;

  /// 分类描述
  String description;

  /// 创建日期
  @Property(type: PropertyType.date)
  DateTime createAt;

  BookCategory({this.id = 0, required this.name, this.description = '', DateTime? createAt})
    : createAt = createAt ?? DateTime.now();
}
