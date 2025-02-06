import 'package:flutter_settings_screens/flutter_settings_screens.dart';

import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_provider.dart';
import 'package:daily_satori/objectbox.g.dart';

class SettingService {
  SettingService._privateConstructor();
  static final SettingService _instance = SettingService._privateConstructor();
  static SettingService get i => _instance;

  static String openAITokenKey = 'openai_token';
  static String openAIAddressKey = 'openai_address';
  static String aiModelKey = 'ai_model';
  static String backupDirKey = 'backup_dir';
  static String pluginKey = 'plugin_url';
  static String webServerPasswordKey = 'web_server_password';

  // 配置的初始值
  static Map<String, String> defaultSettings = {
    openAITokenKey: '',
    openAIAddressKey: 'https://api.openai.com/v1',
    aiModelKey: 'deepseek-v3',
    backupDirKey: '',
    pluginKey: 'https://raw.githubusercontent.com/SatoriTours/plugin/refs/heads/main',
    webServerPasswordKey: 'gja0dNVk',
  };

  Future<void> init() async {
    logger.i("[初始化服务] SettingsService");
    _initDefaultSettings();
    await Settings.init(cacheProvider: SettingProvider());
  }

  final settingBox = ObjectboxService.i.box<Setting>();

  bool aiEnabled() {
    final apiKey = getSetting(SettingService.openAITokenKey);
    final baseUrl = getSetting(SettingService.openAIAddressKey);
    final isEnabled = apiKey.isNotEmpty && baseUrl.isNotEmpty;
    logger.i("[AI] 是否启用: $isEnabled");
    return isEnabled;
  }

  // 初始化默认配置到数据库
  Future<void> _initDefaultSettings() async {
    for (var entry in defaultSettings.entries) {
      final key = entry.key;
      final value = entry.value;
      final existing = settingBox.query(Setting_.key.equals(key)).build().findFirst();
      if (existing == null && value.isNotEmpty) {
        settingBox.put(Setting(key: key, value: value));
      }
    }
  }

  bool containsKey(String key) {
    final existing = settingBox.query(Setting_.key.equals(key)).build().findFirst();
    return existing != null;
  }

  Future<void> saveSetting(String key, String value) async {
    if (key.isEmpty || value.isEmpty) {
      return;
    }
    logger.i("[更新Settings] key => $key, value => $value");
    final existing = settingBox.query(Setting_.key.equals(key)).build().findFirst();
    if (existing != null) {
      // 如果存在，更新值
      existing.value = value;
      settingBox.put(existing);
    } else {
      // 如果不存在，创建新的设置
      settingBox.put(Setting(key: key, value: value));
    }
  }

  Future<void> saveSettings(Map<String, String> settings) async {
    if (settings.isEmpty) return;

    try {
      // 1. 获取所有需要更新的 key 对应的现有设置
      final keys = settings.keys.toList();
      final query = settingBox.query(Setting_.key.oneOf(keys)).build();
      final existingSettings = query.find();
      query.close();

      // 2. 将现有设置转换为 Map，方便查找
      final existingMap = {for (var setting in existingSettings) setting.key: setting};

      // 3. 准备要更新的设置列表
      final settingsToUpdate = <Setting>[];

      // 4. 遍历需要保存的设置
      settings.forEach((key, value) {
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
      settingBox.putMany(settingsToUpdate);

      logger.i("[批量更新Settings] 更新 ${settingsToUpdate.length} 条设置");
    } catch (e) {
      logger.e("[批量更新Settings失败] $e");
    }
  }

  String getSetting(String key, {defaultValue = ''}) {
    final setting = settingBox.query(Setting_.key.equals(key)).build().findFirst();
    return setting?.value ?? defaultValue;
  }

  Set getKeys() {
    final settings = settingBox.getAll();
    return settings.where((s) => s.key != null).map((s) => s.key!).toSet();
  }

  Future<void> remove(String key) async {
    final existing = settingBox.query(Setting_.key.equals(key)).build().findFirst();
    if (existing != null) {
      settingBox.remove(existing.id);
    }
  }

  Future<void> removeAll() async {
    settingBox.removeAll();
  }
}
