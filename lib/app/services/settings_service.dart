import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

class SettingsService {
  SettingsService._privateConstructor();
  static final SettingsService _instance = SettingsService._privateConstructor();
  static SettingsService get i => _instance;

  static String openAITokenKey = 'openai_token';
  static String openAIAddressKey = 'openai_address';
  static String backupDirKey = 'backup_dir';

  Future<void> init() async {
    logger.i("[初始化服务] SettingsService");
    await reloadSettings();
  }

  late final Map<String, String> _settings = <String, String>{};
  final settingBox = ObjectboxService.i.box<Setting>();

  bool aiEnabled() {
    final apiKey = getSetting(SettingsService.openAITokenKey);
    final baseUrl = getSetting(SettingsService.openAIAddressKey);
    final isEnabled = apiKey.isNotEmpty && baseUrl.isNotEmpty;
    logger.i("[AI] 是否启用: $isEnabled");
    return isEnabled;
  }

  bool containsKey(String key) {
    return _settings.containsKey(key);
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

    await reloadSettings();
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

    await reloadSettings();
  }

  Future<void> reloadSettings() async {
    final settings = settingBox.getAll();
    for (var setting in settings) {
      if (setting.key != null) {
        _settings[setting.key!] = setting.value ?? '';
      }
    }
    AiService.i.reloadClient();
  }

  String getSetting(String key, {defaultValue = ''}) {
    return _settings[key] ?? defaultValue;
  }

  Set getKeys() {
    return _settings.keys.toSet();
  }

  Future<void> remove(String key) async {
    final existing = settingBox.query(Setting_.key.equals(key)).build().findFirst();
    if (existing != null) {
      settingBox.remove(existing.id);
    }
    await reloadSettings();
  }

  Future<void> removeAll() async {
    settingBox.removeAll();
    await reloadSettings();
  }
}
