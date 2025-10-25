import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/migration_service/migration_task.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:flutter_settings_screens/flutter_settings_screens.dart';

/// AI配置迁移任务 - 将AI配置从Settings迁移到专用AIConfig
class AIConfigMigrationTask extends MigrationTask {
  @override
  int get version => 1;

  @override
  String get description => "AI配置从Settings到AIConfig的迁移";

  @override
  Future<void> migrate() async {
    logInfo("开始AI配置迁移");

    try {
      // 获取现有配置数据
      final oldConfig = _getOldAIConfig();

      // 检查是否需要迁移
      if (!_isAIConfigMigrationNeeded(oldConfig)) {
        logSuccess("AI配置无需迁移");
        return;
      }

      // 获取或创建通用配置
      final generalConfig = await _getOrCreateGeneralConfig();
      if (generalConfig == null) {
        return;
      }

      // 更新配置
      _updateGeneralConfig(generalConfig, oldConfig);

      // 清除旧配置
      _clearOldAIConfig();

      logSuccess("AI配置迁移完成");
    } catch (e, stackTrace) {
      logError("AI配置迁移失败", error: e, stackTrace: stackTrace);
    }
  }

  /// 获取旧的AI配置数据
  Map<String, String> _getOldAIConfig() {
    return {
      'apiToken': Settings.getValue<String>(SettingService.openAITokenKey) ?? '',
      'apiAddress':
          Settings.getValue<String>(SettingService.openAIAddressKey) ??
          SettingService.defaultSettings[SettingService.openAIAddressKey] ??
          '',
      'modelName': Settings.getValue<String>('ai_model') ?? SettingService.defaultSettings['ai_model'] ?? '',
    };
  }

  /// 判断是否需要进行AI配置迁移
  bool _isAIConfigMigrationNeeded(Map<String, String> oldConfig) {
    final apiToken = oldConfig['apiToken'] ?? '';
    final apiAddress = oldConfig['apiAddress'] ?? '';
    final modelName = oldConfig['modelName'] ?? '';

    // 如果所有设置都为空或为默认值，则不需要迁移
    return !(apiToken.isEmpty &&
        (apiAddress.isEmpty || apiAddress == SettingService.defaultSettings[SettingService.openAIAddressKey]) &&
        (modelName.isEmpty || modelName == SettingService.defaultSettings['ai_model']));
  }

  /// 获取或创建通用AI配置
  Future<AIConfigModel?> _getOrCreateGeneralConfig() async {
    // 获取通用配置
    AIConfigModel? generalConfig = AIConfigRepository.instance.getGeneralConfig();

    // 如果不存在，则创建默认配置
    if (generalConfig == null) {
      logInfo("创建默认AI配置");
      AIConfigRepository.instance.initDefaultConfigs();
      generalConfig = AIConfigRepository.instance.getGeneralConfig();

      if (generalConfig == null) {
        logError("创建通用配置失败");
        return null;
      }
    }

    return generalConfig;
  }

  /// 使用旧配置更新通用配置
  void _updateGeneralConfig(AIConfigModel generalConfig, Map<String, String> oldConfig) {
    logInfo("更新通用AI配置");

    final apiToken = oldConfig['apiToken'] ?? '';
    final apiAddress = oldConfig['apiAddress'] ?? '';
    final modelName = oldConfig['modelName'] ?? '';

    // 只更新非空/非默认值
    if (apiToken.isNotEmpty) {
      generalConfig.apiToken = apiToken;
    }

    if (apiAddress.isNotEmpty && apiAddress != SettingService.defaultSettings[SettingService.openAIAddressKey]) {
      generalConfig.apiAddress = apiAddress;
    }

    if (modelName.isNotEmpty && modelName != SettingService.defaultSettings['ai_model']) {
      generalConfig.modelName = modelName;
    }

    // 保存更新后的配置
    AIConfigRepository.instance.updateAIConfig(generalConfig);
  }

  /// 清除旧的AI配置数据
  void _clearOldAIConfig() {
    logInfo("清除旧AI配置数据");
    SettingRepository.instance.removeSetting(SettingService.openAITokenKey);
    SettingRepository.instance.removeSetting(SettingService.openAIAddressKey);
    SettingRepository.instance.removeSetting('ai_model');
  }
}
