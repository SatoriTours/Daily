import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';

/// 标签数据模型类
///
/// 封装Tag实体类，提供属性访问方法
class TagModel {
  /// 底层实体对象
  final Tag _entity;

  /// 构造函数
  TagModel(this._entity);

  /// 从ID创建实例
  factory TagModel.fromId(int id) {
    final tag = TagRepository.find(id);
    if (tag == null) {
      throw Exception('找不到ID为$id的标签');
    }
    return tag;
  }

  /// 从名称创建实例
  factory TagModel.fromName(String name) {
    var tag = TagRepository.findByName(name);
    if (tag == null) {
      // 如果不存在，则创建一个新标签
      tag = TagModel(Tag(name: name));
      TagRepository.create(tag);
    }
    return tag;
  }

  /// 获取底层实体
  Tag get entity => _entity;

  /// ID
  int get id => _entity.id;

  /// 名称
  String? get name => _entity.name;
  set name(String? value) => _entity.name = value;

  /// 图标
  String? get icon => _entity.icon;
  set icon(String? value) => _entity.icon = value;

  /// 保存模型
  Future<void> save() async {
    await TagRepository.update(this);
  }
}
