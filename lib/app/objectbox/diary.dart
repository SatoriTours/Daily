import 'package:objectbox/objectbox.dart';

@Entity()
class Diary {
  @Id()
  int id;

  String content;

  DateTime createdAt;
  DateTime updatedAt;

  // 标签，用于分类日记内容
  String? tags;

  // 心情标记，可以记录当天的心情
  String? mood;

  // 可能包含的图片路径列表，以英文逗号分隔
  String? images;

  Diary({
    this.id = 0,
    required this.content,
    DateTime? createdAt,
    DateTime? updatedAt,
    this.tags,
    this.mood,
    this.images,
  }) : this.createdAt = createdAt ?? DateTime.now(),
       this.updatedAt = updatedAt ?? DateTime.now();
}
