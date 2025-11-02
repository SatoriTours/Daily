import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/screenshot_repository.dart';

/// 截图数据模型类
class ScreenshotModel extends EntityModel<Screenshot> {
  ScreenshotModel(super.entity);

  factory ScreenshotModel.fromId(int id) {
    final screenshot = ScreenshotRepository.instance.findModel(id);
    if (screenshot == null) {
      throw Exception('找不到ID为$id的截图');
    }
    return screenshot;
  }

  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  int? get articleId => entity.article.targetId;

  void save() {
    ScreenshotRepository.instance.save(this);
  }
}
