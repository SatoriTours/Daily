import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/models/setting_model.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 设置仓储类
///
/// 提供操作设置实体的静态方法集合
class SettingRepository {
  // 私有构造函数防止实例化
  SettingRepository._();

  // 获取Box的静态方法
  static Box<Setting> get _box => ObjectboxService.i.box<Setting>();

  /// 查找所有设置
  static List<SettingModel> all() {
    return _box.getAll().map((e) => SettingModel(e)).toList();
  }

  /// 根据ID查找设置
  static SettingModel? find(int id) {
    final setting = _box.get(id);
    return setting != null ? SettingModel(setting) : null;
  }

  /// 根据键查找设置
  static SettingModel? findByKey(String key) {
    final query = _box.query(Setting_.key.equals(key)).build();
    final setting = query.findFirst();
    query.close();
    return setting != null ? SettingModel(setting) : null;
  }

  /// 获取设置值
  static String? getValue(String key, {String? defaultValue}) {
    final settingModel = findByKey(key);
    return settingModel?.value ?? defaultValue;
  }

  /// 设置值
  static Future<int> setValue(String key, String value) async {
    var settingModel = findByKey(key);
    if (settingModel != null) {
      // 更新现有设置
      settingModel.value = value;
      return await update(settingModel);
    } else {
      // 创建新设置
      final setting = Setting(key: key, value: value);
      final settingModel = SettingModel(setting);
      return await create(settingModel);
    }
  }

  /// 保存设置
  static Future<int> create(SettingModel settingModel) async {
    return await _box.putAsync(settingModel.entity);
  }

  /// 更新设置
  static Future<int> update(SettingModel settingModel) async {
    return await _box.putAsync(settingModel.entity);
  }

  /// 删除设置
  static bool destroy(int id) {
    return _box.remove(id);
  }
}
