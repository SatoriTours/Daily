import 'package:daily_satori/app/objectbox/diary.dart';

/// 日记模型类，包装Diary实体
class DiaryModel {
  int id;
  String content;
  DateTime createdAt;
  DateTime updatedAt;
  String? tags;
  String? mood;
  String? images;

  DiaryModel({
    this.id = 0,
    required this.content,
    DateTime? createdAt,
    DateTime? updatedAt,
    this.tags,
    this.mood,
    this.images,
  }) : this.createdAt = createdAt ?? DateTime.now(),
       this.updatedAt = updatedAt ?? DateTime.now();

  /// 从Diary实体创建DiaryModel
  factory DiaryModel.fromEntity(Diary entity) {
    return DiaryModel(
      id: entity.id,
      content: entity.content,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
      tags: entity.tags,
      mood: entity.mood,
      images: entity.images,
    );
  }

  /// 转换为Diary实体
  Diary toEntity() {
    return Diary(
      id: id,
      content: content,
      createdAt: createdAt,
      updatedAt: updatedAt,
      tags: tags,
      mood: mood,
      images: images,
    );
  }
}
