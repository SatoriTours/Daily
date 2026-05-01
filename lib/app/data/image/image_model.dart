import 'package:daily_satori/app/data/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/image.dart';

/// 图片数据模型类
class ImageModel extends EntityModel<Image> {
  ImageModel(super.entity);

  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  int? get articleId => entity.article.targetId;
}
