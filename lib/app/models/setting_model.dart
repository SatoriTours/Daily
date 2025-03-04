import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 设置模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class SettingModel extends BaseModel<Setting> {
  // 单例实现
  static final SettingModel _instance = SettingModel._internal();
  static SettingModel get i => _instance;
  factory SettingModel() => _instance;
  SettingModel._internal() : super.withEntity(null);

  /// 构造函数，接收一个Setting实体
  SettingModel.withEntity(Setting? setting) : super.withEntity(setting);

  /// 获取设置ID
  @override
  int get id => entity?.id ?? 0;

  /// 设置键
  String? get key => entity?.key;

  /// 设置值
  String? get value => entity?.value;

  /// 静态方法 - 从实体创建模型实例
  static SettingModel fromEntity(Setting setting) {
    return SettingModel.withEntity(setting);
  }

  @override
  BaseModel<Setting> createFromEntity(Setting entity) {
    return SettingModel.fromEntity(entity);
  }

  @override
  Future<int> saveEntity(Setting entity) async {
    return await box.putAsync(entity);
  }

  /// 静态方法 - 查找所有设置
  static List<SettingModel> all() {
    return i.findAll().cast<SettingModel>();
  }

  /// 静态方法 - 根据ID查找设置
  static SettingModel? find(int id) {
    return i.findById(id) as SettingModel?;
  }

  /// 静态方法 - 根据键查找设置
  static SettingModel? findByKey(String key) {
    return i._findByKey(key);
  }

  /// 静态方法 - 获取设置值
  static String? getValue(String key, {String? defaultValue}) {
    return i._getValue(key, defaultValue: defaultValue);
  }

  /// 静态方法 - 设置值
  static Future<int> setValue(String key, String value) async {
    return await i._setValue(key, value);
  }

  /// 静态方法 - 保存设置
  static Future<int> create(SettingModel model) async {
    return await i.saveModel(model);
  }

  /// 静态方法 - 删除设置
  static bool destroy(int id) {
    return i.deleteById(id);
  }

  // 私有方法 - 根据键查找设置
  SettingModel? _findByKey(String key) {
    final query = box.query(Setting_.key.equals(key)).build();
    final setting = query.findFirst();
    query.close();
    return setting != null ? SettingModel.fromEntity(setting) : null;
  }

  // 私有方法 - 获取设置值
  String? _getValue(String key, {String? defaultValue}) {
    final model = _findByKey(key);
    return model?.value ?? defaultValue;
  }

  // 私有方法 - 设置值
  Future<int> _setValue(String key, String value) async {
    var model = _findByKey(key);
    if (model != null) {
      // 更新现有设置
      final setting = model.entity!;
      setting.value = value;
      return await box.putAsync(setting);
    } else {
      // 创建新设置
      final setting = Setting(key: key, value: value);
      return await box.putAsync(setting);
    }
  }
}
