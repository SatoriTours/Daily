import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 设置模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class SettingModel {
  // 单例实现
  static final SettingModel _instance = SettingModel._internal();
  static SettingModel get i => _instance;
  factory SettingModel() => _instance;
  SettingModel._internal();

  // ObjectBox服务和Box访问
  final _objectboxService = ObjectboxService.i;
  Box<Setting> get _settingBox => _objectboxService.box<Setting>();

  // 实体对象
  Setting? _setting;

  /// 构造函数，接收一个Setting实体
  SettingModel.withEntity(this._setting);

  /// 获取原始Setting实体
  Setting? get entity => _setting;

  /// 设置ID
  int get id => _setting?.id ?? 0;

  /// 设置键
  String? get key => _setting?.key;

  /// 设置值
  String? get value => _setting?.value;

  /// 静态方法 - 从实体创建模型实例
  static SettingModel fromEntity(Setting setting) {
    return SettingModel.withEntity(setting);
  }

  /// 静态方法 - 查找所有设置
  static List<SettingModel> all() {
    return i._findAll();
  }

  /// 静态方法 - 根据ID查找设置
  static SettingModel? find(int id) {
    return i._findById(id);
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
    return await i._save(model);
  }

  /// 静态方法 - 删除设置
  static bool destroy(int id) {
    return i._delete(id);
  }

  /// 实例方法 - 保存当前模型
  Future<int> save() async {
    if (_setting == null) return 0;
    return await SettingModel.create(this);
  }

  /// 实例方法 - 删除当前模型
  bool delete() {
    if (_setting == null) return false;
    return SettingModel.destroy(id);
  }

  // 私有方法 - 查找所有设置
  List<SettingModel> _findAll() {
    final settings = _settingBox.getAll();
    return _fromEntityList(settings);
  }

  // 私有方法 - 根据ID查找设置
  SettingModel? _findById(int id) {
    final setting = _settingBox.get(id);
    return setting != null ? SettingModel.fromEntity(setting) : null;
  }

  // 私有方法 - 根据键查找设置
  SettingModel? _findByKey(String key) {
    final query = _settingBox.query(Setting_.key.equals(key)).build();
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
      return await _settingBox.putAsync(setting);
    } else {
      // 创建新设置
      final setting = Setting(key: key, value: value);
      return await _settingBox.putAsync(setting);
    }
  }

  // 私有方法 - 保存设置
  Future<int> _save(SettingModel model) async {
    if (model._setting == null) return 0;
    return await _settingBox.putAsync(model._setting!);
  }

  // 私有方法 - 删除设置
  bool _delete(int id) {
    return _settingBox.remove(id);
  }

  // 私有方法 - 将实体列表转换为模型列表
  List<SettingModel> _fromEntityList(List<Setting> settings) {
    return settings.map((setting) => SettingModel.fromEntity(setting)).toList();
  }
}
