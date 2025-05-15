import 'package:objectbox/objectbox.dart';

@Entity()
class Book {
  @Id()
  int id = 0;

  /// 书名
  String title;

  /// 作者
  String author;

  /// 分类
  String category;

  /// 封面图片
  String coverImage;

  /// 介绍信息
  String introduction;

  /// 是否有更新或未读
  bool hasUpdate;

  /// 创建日期
  @Property(type: PropertyType.date)
  DateTime createAt;

  /// 更新日期
  @Property(type: PropertyType.date)
  DateTime updateAt;

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
  }) : createAt = createAt ?? DateTime.now(),
       updateAt = updateAt ?? DateTime.now();
}
