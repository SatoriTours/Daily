import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

@Entity()
class BookViewpoint implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date, uid: 8943367424862014812)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date, uid: 8945662529292125528)
  late DateTime updatedAt;

  int bookId;
  String title;
  String content;
  String example;

  BookViewpoint({
    this.id = 0,
    required this.bookId,
    required this.title,
    required this.content,
    this.example = '',
    DateTime? createAt,
    DateTime? updateAt,
  }) {
    createdAt = createAt ?? DateTime.now();
    updatedAt = updateAt ?? DateTime.now();
  }
}
