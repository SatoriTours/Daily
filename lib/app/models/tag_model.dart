import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 标签模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class TagModel {
  // 单例实现
  static final TagModel _instance = TagModel._internal();
  static TagModel get i => _instance;
  factory TagModel() => _instance;
  TagModel._internal();

  // ObjectBox服务和Box访问
  final _objectboxService = ObjectboxService.i;
  Box<Tag> get _tagBox => _objectboxService.box<Tag>();

  // 实体对象
  Tag? _tag;

  /// 构造函数，接收一个Tag实体
  TagModel.withEntity(this._tag);

  /// 获取原始Tag实体
  Tag? get entity => _tag;

  /// 标签ID
  int get id => _tag?.id ?? 0;

  /// 标签名称
  String? get name => _tag?.name;

  /// 标签图标
  String? get icon => _tag?.icon;

  /// 静态方法 - 从实体创建模型实例
  static TagModel fromEntity(Tag tag) {
    return TagModel.withEntity(tag);
  }

  /// 静态方法 - 查找所有标签
  static List<TagModel> all() {
    return i._findAll();
  }

  /// 静态方法 - 根据ID查找标签
  static TagModel? find(int id) {
    return i._findById(id);
  }

  /// 静态方法 - 根据名称查找标签
  static TagModel? findByName(String name) {
    return i._findByName(name);
  }

  /// 静态方法 - 创建或查找标签
  static TagModel findOrCreate(String name, {String? icon}) {
    return i._findOrCreate(name, icon: icon);
  }

  /// 静态方法 - 保存标签
  static Future<int> create(TagModel model) async {
    return await i._save(model);
  }

  /// 静态方法 - 删除标签
  static bool destroy(int id) {
    return i._delete(id);
  }

  /// 实例方法 - 保存当前模型
  Future<int> save() async {
    if (_tag == null) return 0;
    return await TagModel.create(this);
  }

  /// 实例方法 - 删除当前模型
  bool delete() {
    if (_tag == null) return false;
    return TagModel.destroy(id);
  }

  // 私有方法 - 查找所有标签
  List<TagModel> _findAll() {
    final tags = _tagBox.getAll();
    return _fromEntityList(tags);
  }

  // 私有方法 - 根据ID查找标签
  TagModel? _findById(int id) {
    final tag = _tagBox.get(id);
    return tag != null ? TagModel.fromEntity(tag) : null;
  }

  // 私有方法 - 根据名称查找标签
  TagModel? _findByName(String name) {
    final query = _tagBox.query(Tag_.name.equals(name)).build();
    final tag = query.findFirst();
    query.close();
    return tag != null ? TagModel.fromEntity(tag) : null;
  }

  // 私有方法 - 创建或查找标签
  TagModel _findOrCreate(String name, {String? icon}) {
    var model = _findByName(name);
    if (model != null) return model;

    final tag = Tag(name: name, icon: icon);
    final id = _tagBox.put(tag);
    tag.id = id;
    return TagModel.fromEntity(tag);
  }

  // 私有方法 - 保存标签
  Future<int> _save(TagModel model) async {
    if (model._tag == null) return 0;
    return await _tagBox.putAsync(model._tag!);
  }

  // 私有方法 - 删除标签
  bool _delete(int id) {
    return _tagBox.remove(id);
  }

  // 私有方法 - 将实体列表转换为模型列表
  List<TagModel> _fromEntityList(List<Tag> tags) {
    return tags.map((tag) => TagModel.fromEntity(tag)).toList();
  }
}
