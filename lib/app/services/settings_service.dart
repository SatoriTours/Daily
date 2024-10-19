import 'package:get/get.dart';
import 'package:sqflite/sqflite.dart';

import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/global.dart';

class SettingsService extends GetxService {
  SettingsService._privateConstructor();
  static final SettingsService _instance =
      SettingsService._privateConstructor();
  static SettingsService get instance => _instance;

  static String openAITokenKey = 'openai_token';
  static String openAIAddressKey = 'openai_address';
  static String backupDirKey = 'backup_dir';

  Future<void> init() async {
    await reloadSettings();
  }

  final String _tableName = 'settings';
  late final Map<String, String> _settings = <String, String>{};
  final _db = DatabaseService.instance.database;

  void saveSetting(String key, String value) async {
    if (key.isEmpty || value.isEmpty) {
      return;
    }
    await _db.insert(
      _tableName,
      updateTimestamps({'key': key, 'value': value}),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
    reloadSettings();
  }

  Future<void> saveSettings(Map<String, String> settings) async {
    final batch = _db.batch();
    for (var setting in settings.entries) {
      if (setting.key.isEmpty || setting.value.isEmpty) {
        continue;
      }
      batch.insert(
        _tableName,
        updateTimestamps({'key': setting.key, 'value': setting.value}),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    ;
    await batch.commit();
    reloadSettings();
  }

  Future<void> reloadSettings() async {
    final List<Map<String, dynamic>> maps = await _db.query(_tableName);
    for (var row in maps) {
      if (row['key'] != null && row['value'] != null) {
        _settings[row['key']] = row['value'];
      }
    }
  }

  String getSetting(String key, {defaultValue = ''}) {
    return _settings[key] ?? defaultValue;
  }
}
