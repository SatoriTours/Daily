import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';

/// AI配置服务类
///
/// 负责提供AI配置的业务逻辑
class AIConfigService implements AppService {
  // MARK: - 单例实现
  AIConfigService._privateConstructor();
  static final AIConfigService _instance = AIConfigService._privateConstructor();
  static AIConfigService get i => _instance;

  @override
  String get serviceName => 'AIConfigService';

  @override
  ServicePriority get priority => ServicePriority.high;

  // MARK: - 方法

  /// 初始化
  @override
  Future<void> init() async {
    logger.i("[AI配置服务] 初始化");
    AIConfigRepository.initDefaultConfigs();
  }

  /// 获取通用配置
  AIConfigModel? getGeneralConfig() {
    return AIConfigRepository.getGeneralConfig();
  }

  /// 获取特定功能类型的默认配置
  AIConfigModel? getDefaultConfig(int functionType) {
    try {
      // 获取指定功能的默认配置
      final defaultConfig = AIConfigRepository.getDefaultAIConfigByFunctionType(functionType);

      // 如果配置需要继承自通用配置，则应用继承
      if (defaultConfig != null && defaultConfig.inheritFromGeneral) {
        final generalConfig = getGeneralConfig();
        if (generalConfig != null) {
          defaultConfig.inheritFromConfig(generalConfig);
        }
      }

      return defaultConfig;
    } catch (e, stackTrace) {
      logger.e("[AI配置服务] 获取默认配置失败: $e", stackTrace: stackTrace);
      return null;
    }
  }

  /// 获取AI功能类型名称
  String getFunctionTypeName(int functionType) {
    switch (functionType) {
      case 1:
        return "文章分析";
      case 2:
        return "书本解读";
      case 3:
        return "日记总结";
      case 0:
        return "通用配置";
      default:
        return "未知";
    }
  }

  /// 为特定功能获取API地址
  String getApiAddressForFunction(int functionType) {
    final config = getDefaultConfig(functionType);
    return config?.apiAddress ?? "";
  }

  /// 为特定功能获取API令牌
  String getApiTokenForFunction(int functionType) {
    final config = getDefaultConfig(functionType);
    return config?.apiToken ?? "";
  }

  /// 为特定功能获取模型名称
  String getModelNameForFunction(int functionType) {
    final config = getDefaultConfig(functionType);
    return config?.modelName ?? "";
  }

  @override
  void dispose() {}
}
