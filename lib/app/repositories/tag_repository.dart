import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/models/tag_model.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 标签仓储类
///
/// 提供操作标签实体的静态方法集合
class TagRepository {
  // 私有构造函数防止实例化
  TagRepository._();

  // 获取Box的静态方法
  static Box<Tag> get _box => ObjectboxService.i.box<Tag>();

  /// 查找所有标签
  static List<TagModel> all() {
    return _box.getAll().map((e) => TagModel(e)).toList();
  }

  /// 根据ID查找标签
  static TagModel? find(int id) {
    final tag = _box.get(id);
    return tag != null ? TagModel(tag) : null;
  }

  /// 根据名称查找标签
  static TagModel? findByName(String name) {
    final query = _box.query(Tag_.name.equals(name)).build();
    final tag = query.findFirst();
    query.close();
    return tag != null ? TagModel(tag) : null;
  }

  /// 创建或查找标签
  static TagModel findOrCreate(String name, {String? icon}) {
    var tagModel = findByName(name);
    if (tagModel != null) return tagModel;

    final tag = Tag(name: name, icon: icon);
    final id = _box.put(tag);
    tag.id = id;
    return TagModel(tag);
  }

  /// 保存标签
  static Future<int> create(TagModel tagModel) async {
    return await _box.putAsync(tagModel.entity);
  }

  /// 更新标签
  static Future<int> update(TagModel tagModel) async {
    return await _box.putAsync(tagModel.entity);
  }

  /// 删除标签
  static bool destroy(int id) {
    return _box.remove(id);
  }
}
