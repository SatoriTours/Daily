import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/models/setting_model.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 设置仓储类
///
/// 继承 BaseRepository 获取通用 CRUD 功能
/// 使用单例模式，通过 SettingRepository.instance 访问
class SettingRepository extends BaseRepository<Setting, SettingModel> {
  // 私有构造函数
  SettingRepository._();

  // 单例
  static final SettingRepository instance = SettingRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  SettingModel toModel(Setting entity) {
    return SettingModel(entity);
  }

  // ==================== 特定业务方法 ====================

  /// 根据键查找设置
  SettingModel? findByKey(String key) {
    return findFirstByStringEquals(Setting_.key, key);
  }

  /// 检查键是否存在
  bool containsKey(String key) {
    return findFirstByStringEquals(Setting_.key, key) != null;
  }

  /// 获取设置值
  String? getValue(String key, {String? defaultValue}) {
    final settingModel = findByKey(key);
    return settingModel?.entity.value ?? defaultValue;
  }

  /// 设置值
  int setValue(String key, String value) {
    // 空值检查
    if (key.isEmpty || value.isEmpty) {
      return 0;
    }

    var settingModel = findByKey(key);
    if (settingModel != null) {
      // 更新现有设置
      settingModel.entity.value = value;
      return save(settingModel);
    } else {
      // 创建新设置
      final setting = Setting(key: key, value: value);
      final settingModel = SettingModel(setting);
      return save(settingModel);
    }
  }

  /// 批量保存设置
  Future<void> saveSettings(Map<String, String> settings) async {
    if (settings.isEmpty) return;

    try {
      // 1. 获取所有需要更新的 key 对应的现有设置
      final keys = settings.keys.toList();
      final condition = Setting_.key.oneOf(keys);
      final existingSettings = findByCondition(condition);

      // 2. 将现有设置转换为 Map，方便查找
      final existingMap = {for (var model in existingSettings) model.entity.key: model};

      // 3. 准备要更新的设置模型列表
      final modelsToUpdate = <SettingModel>[];

      // 4. 遍历需要保存的设置
      settings.forEach((key, value) {
        if (key.isEmpty || value.isEmpty) return;

        if (existingMap.containsKey(key)) {
          // 更新现有设置
          existingMap[key]!.entity.value = value;
          modelsToUpdate.add(existingMap[key]!);
        } else {
          // 创建新设置
          modelsToUpdate.add(SettingModel(Setting(key: key, value: value)));
        }
      });

      // 5. 批量保存所有设置
      await saveMany(modelsToUpdate);

      logger.i("[设置仓储] 批量更新 ${modelsToUpdate.length} 条设置");
    } catch (e) {
      logger.e("[设置仓储] 批量更新失败: $e");
    }
  }

  /// 获取所有键的集合
  Set<String> getKeys() {
    final settings = all();
    return settings.where((s) => s.entity.key != null).map((s) => s.entity.key!).toSet();
  }

  /// 根据键删除设置
  bool removeByKey(String key) {
    final existing = findFirstByStringEquals(Setting_.key, key);
    if (existing != null) {
      return remove(existing.entity.id);
    }
    return false;
  }

  /// 删除设置（旧方法名兼容）
  bool destroy(int id) {
    return remove(id);
  }

  /// 删除所有设置（旧方法名兼容）
  void clearAll() {
    removeAll();
  }

  /// 获取设置值
  String getSetting(String key, {String defaultValue = ''}) {
    return getValue(key, defaultValue: defaultValue) ?? defaultValue;
  }

  /// 保存单个设置
  void saveSetting(String key, String value) {
    if (key.isEmpty) return;
    setValue(key, value);
  }

  /// 检查AI功能是否启用
  bool aiEnabled(String openAITokenKey, String openAIAddressKey) {
    final apiKey = getSetting(openAITokenKey);
    final baseUrl = getSetting(openAIAddressKey);
    final isEnabled = apiKey.isNotEmpty && baseUrl.isNotEmpty;
    logger.i("[设置仓储] AI是否启用: $isEnabled");
    return isEnabled;
  }

  /// 初始化默认设置
  void initDefaultSettings(Map<String, String> defaultSettings) {
    for (var entry in defaultSettings.entries) {
      final key = entry.key;
      final value = entry.value;
      if (!containsKey(key) && value.isNotEmpty) {
        setValue(key, value);
      }
    }
    logger.i("[设置仓储] 初始化默认设置完成");
  }

  // 根据 key 删除某个配置
  void removeSetting(String key) {
    final existing = box.query(Setting_.key.equals(key)).build().findFirst();
    if (existing != null) {
      box.remove(existing.id);
    }
  }

  /// 根据ID删除设置
  @override
  bool remove(int id) {
    return box.remove(id);
  }

  /// 删除所有设置
  @override
  int removeAll() {
    return box.removeAll();
  }

  /// 重置所有设置到默认值
  void resetAllSettings() {
    try {
      // 获取所有设置键
      final keys = getKeys();

      // 保留某些系统关键设置，如设备ID等
      final keysToPreserve = <String>{'device_id', 'installation_id'};

      // 删除非保留的设置
      for (final key in keys) {
        if (!keysToPreserve.contains(key)) {
          removeByKey(key);
        }
      }

      logger.i("[设置仓储] 设置已重置为默认值");
    } catch (e) {
      logger.e("[设置仓储] 重置设置失败: $e");
      rethrow;
    }
  }
}
