import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/global.dart';
import 'package:drift/drift.dart' as drift;
import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/db_service.dart';

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
  final _db = DBService.i.db;

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
    try {
      await _db.into(_db.settings).insertOnConflictUpdate(
            SettingsCompanion(
              key: drift.Value(key),
              value: drift.Value(value),
            ),
          );
    } catch (e) {
      logger.i("[更新Settings失败] key => $key, value => $value, $e");
    }

    // SettingsCompanion.insert(key: key, value: value);
    await reloadSettings();
  }

  Future<void> saveSettings(Map<String, String> settings) async {
    var settingsCompanions = settings.keys.map((key) => SettingsCompanion.insert(key: key, value: settings[key]!));
    await _db.batch((batch) {
      batch.insertAllOnConflictUpdate(_db.settings, settingsCompanions);
    });

    await reloadSettings();
  }

  Future<void> reloadSettings() async {
    final rows = await _db.select(_db.settings).get();
    for (var row in rows) {
      _settings[row.key] = row.value;
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
    await (_db.delete(_db.settings)..where((t) => t.key.equals(key))).go();
    await reloadSettings();
  }

  Future<void> removeAll() async {
    await (_db.delete(_db.settings)).go();
    await reloadSettings();
  }
}
