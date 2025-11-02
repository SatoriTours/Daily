import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/repositories/image_repository.dart';

/// 图片数据模型类
class ImageModel extends EntityModel<Image> {
  ImageModel(super.entity);

  factory ImageModel.fromId(int id) {
    final image = ImageRepository.i.findModel(id);
    if (image == null) {
      throw Exception('找不到ID为$id的图片');
    }
    return image;
  }

  String? get path => entity.path;
  set path(String? value) => entity.path = value;

  int? get articleId => entity.article.targetId;

  void save() {
    ImageRepository.i.save(this);
  }
}
