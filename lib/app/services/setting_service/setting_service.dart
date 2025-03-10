import 'package:flutter_settings_screens/flutter_settings_screens.dart';

import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_provider.dart';
import 'package:daily_satori/app/utils/random_utils.dart';

/// 设置服务类
///
/// 只提供设置键常量和初始化方法
/// 不再提供任何业务逻辑或代理方法，所有设置操作应直接调用 SettingRepository
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

  // MARK: - 初始化方法
  Future<void> init() async {
    logger.i("[设置服务] 初始化");
    await SettingRepository.initDefaultSettings(defaultSettings);
    await Settings.init(cacheProvider: SettingProvider());
  }
}
