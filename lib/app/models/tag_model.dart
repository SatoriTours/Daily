import 'package:daily_satori/app/models/mixins/entity_model_mixin.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';

/// 标签数据模型类
///
/// 封装Tag实体类，提供属性访问方法
class TagModel with EntityModelMixin<Tag> {
  final Tag _entity;

  /// 构造函数
  TagModel(this._entity);

  /// 从ID创建实例
  factory TagModel.fromId(int id) {
    final tag = TagRepository.instance.findModel(id);
    if (tag == null) {
      throw Exception('找不到ID为$id的标签');
    }
    return tag;
  }

  /// 从名称创建实例
  factory TagModel.fromName(String name) {
    var tag = TagRepository.instance.findByName(name);
    if (tag == null) {
      // 如果不存在，则创建一个新标签
      tag = TagModel(Tag(name: name));
      TagRepository.instance.createModel(tag);
    }
    return tag;
  }

  /// 底层实体对象
  @override
  Tag get entity => _entity;

  /// ID
  @override
  int get id => entity.id;

  /// 名称
  String? get name => entity.name;
  set name(String? value) => entity.name = value;

  /// 图标
  String? get icon => entity.icon;
  set icon(String? value) => entity.icon = value;

  /// 创建时间 - Tag实体不包含此字段
  @override
  DateTime? get createdAt => null;
  @override
  set createdAt(DateTime? value) {}

  /// 更新时间 - Tag实体不包含此字段
  @override
  DateTime? get updatedAt => null;
  @override
  set updatedAt(DateTime? value) {}

  /// 保存模型
  Future<void> save() async {
    await TagRepository.instance.updateModel(this);
  }
}
