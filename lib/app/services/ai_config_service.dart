import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';

class AIConfigService extends AppService {
  static final AIConfigService _instance = AIConfigService._();
  static AIConfigService get i => _instance;
  AIConfigService._();

  @override
  ServicePriority get priority => ServicePriority.high;

  @override
  Future<void> init() async => AIConfigRepository.i.initDefaultConfigs();

  AIConfigModel? getGeneralConfig() => AIConfigRepository.i.getGeneralConfig();

  AIConfigModel? getDefaultConfig(AIFunctionType type) {
    try {
      final config = AIConfigRepository.i.getDefaultAIConfigByFunctionTypeEnum(type);
      if (config?.inheritFromGeneral == true) {
        final general = getGeneralConfig();
        if (general != null) config?.inheritFromConfig(general);
      }
      return config;
    } catch (e, stackTrace) {
      logger.e('[AI配置服务] 获取默认配置失败: $e', stackTrace: stackTrace);
      return null;
    }
  }

  String getFunctionTypeName(AIFunctionType type) => type.displayName;

  String getConfigValue(AIFunctionType type, String Function(AIConfigModel?) getter) =>
      getter(getDefaultConfig(type));

  String getApiAddressForFunction(AIFunctionType type) =>
      getConfigValue(type, (c) => c?.apiAddress ?? '');

  String getApiTokenForFunction(AIFunctionType type) =>
      getConfigValue(type, (c) => c?.apiToken ?? '');

  String getModelNameForFunction(AIFunctionType type) =>
      getConfigValue(type, (c) => c?.modelName ?? '');

  @override
  void dispose() {}
}
