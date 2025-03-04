import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 标签模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class TagModel extends BaseModel<Tag> {
  // 单例实现
  static final TagModel _instance = TagModel._internal();
  static TagModel get i => _instance;
  factory TagModel() => _instance;
  TagModel._internal() : super.withEntity(null);

  /// 构造函数，接收一个Tag实体
  TagModel.withEntity(Tag? tag) : super.withEntity(tag);

  @override
  int get id => entity?.id ?? 0;

  /// 标签名称
  String? get name => entity?.name;

  /// 标签图标
  String? get icon => entity?.icon;

  @override
  TagModel _createFromEntity(Tag entity) {
    return TagModel.withEntity(entity);
  }

  @override
  Future<int> _saveEntity(Tag entity) async {
    return await box.putAsync(entity);
  }

  /// 静态方法 - 从实体创建模型实例
  static TagModel fromEntity(Tag tag) {
    return TagModel.withEntity(tag);
  }

  /// 静态方法 - 查找所有标签
  static List<TagModel> all() {
    return i.findAll().cast<TagModel>();
  }

  /// 静态方法 - 根据ID查找标签
  static TagModel? find(int id) {
    return i.findById(id) as TagModel?;
  }

  /// 静态方法 - 根据名称查找标签
  static TagModel? findByName(String name) {
    final query = i.box.query(Tag_.name.equals(name)).build();
    final tag = query.findFirst();
    query.close();
    return tag != null ? TagModel.fromEntity(tag) : null;
  }

  /// 静态方法 - 创建或查找标签
  static TagModel findOrCreate(String name, {String? icon}) {
    var model = findByName(name);
    if (model != null) return model;

    final tag = Tag(name: name, icon: icon);
    final id = i.box.put(tag);
    tag.id = id;
    return TagModel.fromEntity(tag);
  }

  /// 静态方法 - 保存标签
  static Future<int> create(TagModel model) async {
    return await i.saveModel(model);
  }

  /// 静态方法 - 删除标签
  static bool destroy(int id) {
    return i.deleteById(id);
  }
}
