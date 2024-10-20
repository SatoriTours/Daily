import 'package:daily_satori/global.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/db_service.dart';

class SettingsService extends GetxService {
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

  void saveSetting(String key, String value) async {
    if (key.isEmpty || value.isEmpty) {
      return;
    }

    SettingsCompanion.insert(key: key, value: value);
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
  }

  String getSetting(String key, {defaultValue = ''}) {
    return _settings[key] ?? defaultValue;
  }
}
