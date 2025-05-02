import 'package:objectbox/objectbox.dart';
import 'package:json_annotation/json_annotation.dart';

@Entity()
@JsonSerializable()
class BookViewpoint {
  @Id()
  int id = 0;

  /// 关联的书籍ID
  int bookId;

  /// 观点标题
  String title;

  /// 观点内容
  String content;

  /// 案例
  String example;

  /// 创建日期
  @Property(type: PropertyType.date)
  DateTime createAt;

  /// 更新日期
  @Property(type: PropertyType.date)
  DateTime updateAt;

  BookViewpoint({
    this.id = 0,
    required this.bookId,
    required this.title,
    required this.content,
    this.example = '',
    DateTime? createAt,
    DateTime? updateAt,
  }) : createAt = createAt ?? DateTime.now(),
       updateAt = updateAt ?? DateTime.now();
}
