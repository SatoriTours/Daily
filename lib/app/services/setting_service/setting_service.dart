import 'package:flutter_settings_screens/flutter_settings_screens.dart';

import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_provider.dart';
import 'package:daily_satori/app/utils/random_utils.dart';

/// 设置服务类
///
/// 负责管理应用设置的访问、缓存和业务逻辑
class SettingService {
  // MARK: - 单例实现
  SettingService._privateConstructor();
  static final SettingService _instance = SettingService._privateConstructor();
  static SettingService get i => _instance;

  // MARK: - 设置键常量
  static String openAITokenKey = 'openai_token';
  static String openAIAddressKey = 'openai_address';
  static String aiModelKey = 'ai_model';
  static String backupDirKey = 'backup_dir';
  static String pluginKey = 'plugin_url';
  static String webServerPasswordKey = 'web_server_password';
  static String deviceIdKey = 'device_id';
  static String webSocketUrlKey = 'web_socket_url';

  // MARK: - 默认设置值
  static Map<String, String> defaultSettings = {
    openAITokenKey: '',
    openAIAddressKey: 'https://api.openai.com/v1',
    aiModelKey: 'deepseek-v3',
    backupDirKey: '',
    pluginKey: 'https://raw.githubusercontent.com/SatoriTours/plugin/refs/heads/main',
    webServerPasswordKey: RandomUtils.generateRandomPassword(),
    deviceIdKey: RandomUtils.generateDeviceId(),
    webSocketUrlKey: 'ws://10.0.2.2:3000/ws',
  };

  // MARK: - 内存缓存
  final Map<String, String> _cache = {};

  // MARK: - 初始化方法
  Future<void> init() async {
    logger.i("[设置服务] 初始化");
    await _initDefaultSettings();
    await _initCache();
    await Settings.init(cacheProvider: SettingProvider());
  }

  // MARK: - 业务方法

  /// 检查AI是否启用
  bool aiEnabled() {
    final apiKey = getSetting(SettingService.openAITokenKey);
    final baseUrl = getSetting(SettingService.openAIAddressKey);
    final isEnabled = apiKey.isNotEmpty && baseUrl.isNotEmpty;
    logger.i("[设置服务] AI是否启用: $isEnabled");
    return isEnabled;
  }

  // MARK: - 内部方法

  /// 初始化默认设置
  Future<void> _initDefaultSettings() async {
    for (var entry in defaultSettings.entries) {
      final key = entry.key;
      final value = entry.value;
      if (!SettingRepository.containsKey(key) && value.isNotEmpty) {
        await SettingRepository.setValue(key, value);
        _cache[key] = value;
      }
    }
  }

  /// 初始化缓存
  Future<void> _initCache() async {
    final allSettings = SettingRepository.all();
    for (var setting in allSettings) {
      if (setting.key != null && setting.value != null) {
        _cache[setting.key!] = setting.value!;
      }
    }
    logger.i("[设置服务] 缓存了 ${_cache.length} 条设置");
  }

  // MARK: - 公共接口方法

  /// 检查设置键是否存在
  bool containsKey(String key) {
    if (_cache.containsKey(key)) return true;
    return SettingRepository.containsKey(key);
  }

  /// 保存单个设置
  Future<void> saveSetting(String key, String value) async {
    if (key.isEmpty) return;

    await SettingRepository.setValue(key, value);
    if (value.isNotEmpty) {
      _cache[key] = value;
    } else {
      _cache.remove(key);
    }
  }

  /// 批量保存设置
  Future<void> saveSettings(Map<String, String> settings) async {
    if (settings.isEmpty) return;

    await SettingRepository.saveSettings(settings);

    // 更新缓存
    settings.forEach((key, value) {
      if (value.isNotEmpty) {
        _cache[key] = value;
      } else {
        _cache.remove(key);
      }
    });
  }

  /// 获取设置值
  String getSetting(String key, {defaultValue = ''}) {
    // 先从缓存中获取
    if (_cache.containsKey(key)) {
      return _cache[key]!;
    }

    // 从仓库获取，并更新缓存
    final value = SettingRepository.getValue(key, defaultValue: defaultValue) ?? defaultValue;
    if (value.isNotEmpty) {
      _cache[key] = value;
    }

    return value;
  }

  /// 获取所有设置键
  Set getKeys() {
    return SettingRepository.getKeys();
  }

  /// 删除设置
  Future<void> remove(String key) async {
    await SettingRepository.remove(key);
    _cache.remove(key);
  }

  /// 清空所有设置
  Future<void> removeAll() async {
    await SettingRepository.removeAll();
    _cache.clear();
  }

  /// 刷新缓存
  Future<void> refreshCache() async {
    _cache.clear();
    await _initCache();
  }
}
