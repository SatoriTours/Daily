import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';

/// 截图数据模型类
class ScreenshotModel extends EntityModel<Screenshot> {
  ScreenshotModel(super.entity);

  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  int? get articleId => entity.article.targetId;
}
