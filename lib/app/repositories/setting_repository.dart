import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/models/setting_model.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
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

  /// 检查键是否存在
  static bool containsKey(String key) {
    final existing = _box.query(Setting_.key.equals(key)).build().findFirst();
    return existing != null;
  }

  /// 获取设置值
  static String? getValue(String key, {String? defaultValue}) {
    final settingModel = findByKey(key);
    return settingModel?.value ?? defaultValue;
  }

  /// 设置值
  static Future<int> setValue(String key, String value) async {
    // 空值检查
    if (key.isEmpty || value.isEmpty) {
      return 0;
    }

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

  /// 批量保存设置
  static Future<void> saveSettings(Map<String, String> settings) async {
    if (settings.isEmpty) return;

    try {
      // 1. 获取所有需要更新的 key 对应的现有设置
      final keys = settings.keys.toList();
      final query = _box.query(Setting_.key.oneOf(keys)).build();
      final existingSettings = query.find();
      query.close();

      // 2. 将现有设置转换为 Map，方便查找
      final existingMap = {for (var setting in existingSettings) setting.key: setting};

      // 3. 准备要更新的设置列表
      final settingsToUpdate = <Setting>[];

      // 4. 遍历需要保存的设置
      settings.forEach((key, value) {
        if (key.isEmpty || value.isEmpty) return;

        if (existingMap.containsKey(key)) {
          // 更新现有设置
          existingMap[key]!.value = value;
          settingsToUpdate.add(existingMap[key]!);
        } else {
          // 创建新设置
          settingsToUpdate.add(Setting(key: key, value: value));
        }
      });

      // 5. 批量保存所有设置
      _box.putMany(settingsToUpdate);

      logger.i("[设置仓储] 批量更新 ${settingsToUpdate.length} 条设置");
    } catch (e) {
      logger.e("[设置仓储] 批量更新失败: $e");
    }
  }

  /// 获取所有键的集合
  static Set<String> getKeys() {
    final settings = _box.getAll();
    return settings.where((s) => s.key != null).map((s) => s.key!).toSet();
  }

  /// 根据键删除设置
  static Future<bool> remove(String key) async {
    final existing = _box.query(Setting_.key.equals(key)).build().findFirst();
    if (existing != null) {
      return _box.remove(existing.id);
    }
    return false;
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

  /// 删除所有设置
  static Future<void> removeAll() async {
    _box.removeAll();
  }
}
