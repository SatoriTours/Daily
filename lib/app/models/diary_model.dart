import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/diary.dart';
import 'package:daily_satori/app/repositories/repositories.dart';

/// 日记模型类
class DiaryModel extends EntityModel<Diary> {
  DiaryModel(super.entity);

  factory DiaryModel.fromId(int id) {
    final diary = DiaryRepository.i.find(id);
    if (diary == null) {
      throw Exception('找不到ID为$id的日记');
    }
    return diary;
  }

  factory DiaryModel.create({
    int id = 0,
    required String content,
    DateTime? createdAt,
    DateTime? updatedAt,
    String? tags,
    String? mood,
    String? images,
  }) {
    return DiaryModel(
      Diary(
        id: id,
        content: content,
        createdAt: createdAt,
        updatedAt: updatedAt,
        tags: tags,
        mood: mood,
        images: images,
      ),
    );
  }

  // ==================== 基本属性 ====================

  String get content => entity.content;
  set content(String value) => entity.content = value;

  String? get tags => entity.tags;
  set tags(String? value) => entity.tags = value;

  String? get mood => entity.mood;
  set mood(String? value) => entity.mood = value;

  String? get images => entity.images;
  set images(String? value) => entity.images = value;

  // ==================== 计算属性 ====================

  List<String> get imagesList => images?.split(',') ?? [];

  // ==================== 转换方法 ====================

  factory DiaryModel.fromEntity(Diary entity) => DiaryModel(entity);

  Diary toEntity() => entity;
}
