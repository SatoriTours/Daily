import 'package:daily_satori/app/models/mixins/entity_model_mixin.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';

/// 截图数据模型类
class ScreenshotModel with EntityModelMixin<Screenshot> {
  final Screenshot _entity;

  ScreenshotModel(this._entity);

  factory ScreenshotModel.fromId(int id) {
    final screenshot = ScreenshotRepository.instance.findModel(id);
    if (screenshot == null) {
      throw Exception('找不到ID为$id的截图');
    }
    return screenshot;
  }

  @override
  Screenshot get entity => _entity;

  @override
  int get id => entity.id;

  @override
  DateTime? get createdAt => null;
  @override
  set createdAt(DateTime? value) {}

  @override
  DateTime? get updatedAt => null;
  @override
  set updatedAt(DateTime? value) {}

  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  int? get articleId => entity.article.targetId;

  Future<void> save() async {
    ScreenshotRepository.instance.save(entity);
  }
}
