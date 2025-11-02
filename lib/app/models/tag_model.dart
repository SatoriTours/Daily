import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';

/// 标签数据模型类
class TagModel extends EntityModel<Tag> {
  TagModel(super.entity);

  factory TagModel.fromId(int id) {
    final tag = TagRepository.instance.findModel(id);
    if (tag == null) {
      throw Exception('找不到ID为$id的标签');
    }
    return tag;
  }

  factory TagModel.fromName(String name) {
    var tag = TagRepository.instance.findByName(name);
    if (tag == null) {
      tag = TagModel(Tag(name: name));
      TagRepository.instance.createModel(tag);
    }
    return tag;
  }

  String? get name => entity.name;
  set name(String? value) => entity.name = value;

  String? get icon => entity.icon;
  set icon(String? value) => entity.icon = value;

  void save() {
    TagRepository.instance.updateModel(this);
  }
}
