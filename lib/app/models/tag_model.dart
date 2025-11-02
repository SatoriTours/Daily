import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/tag.dart';

/// 标签数据模型类
class TagModel extends EntityModel<Tag> {
  TagModel(super.entity);

  String? get name => entity.name;
  set name(String? value) => entity.name = value;

  String? get icon => entity.icon;
  set icon(String? value) => entity.icon = value;
}
