import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// AI配置存储库
class AIConfigRepository extends BaseRepository<AIConfig, AIConfigModel> {
  // 私有构造函数
  AIConfigRepository._();

  // 单例
  static final AIConfigRepository instance = AIConfigRepository._();

  // 每页数量
  @override
  int get pageSize => 20;

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  AIConfigModel toModel(AIConfig entity) {
    return AIConfigModel(entity);
  }

  @override
  AIConfig toEntity(AIConfigModel model) {
    return model.config;
  }

  /// 添加AI配置
  int addAIConfig(AIConfigModel model) {
    try {
      return save(model.config);
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 添加AI配置失败: $e', stackTrace: stackTrace);
      rethrow;
    }
  }

  /// 获取所有AI配置
  List<AIConfigModel> getAllAIConfigs() {
    try {
      final configs = all();
      return configs.map((config) => AIConfigModel.fromConfig(config)).toList();
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 获取所有AI配置失败: $e', stackTrace: stackTrace);
      return [];
    }
  }

  /// 根据功能类型获取AI配置
  List<AIConfigModel> getAIConfigsByFunctionType(int functionType) {
    try {
      final configs = findByCondition(AIConfig_.functionType.equals(functionType));
      return configs.map((config) => AIConfigModel.fromConfig(config)).toList();
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 根据功能类型获取AI配置失败: $e', stackTrace: stackTrace);
      return [];
    }
  }

  /// 获取通用配置
  AIConfigModel? getGeneralConfig() {
    try {
      final result = findFirstByCondition(AIConfig_.functionType.equals(0));
      return result != null ? AIConfigModel.fromConfig(result) : null;
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 获取通用配置失败: $e', stackTrace: stackTrace);
      return null;
    }
  }

  /// 根据功能类型获取默认AI配置
  AIConfigModel? getDefaultAIConfigByFunctionType(int functionType) {
    try {
      final result = findFirstByCondition(
        AIConfig_.functionType.equals(functionType) & AIConfig_.isDefault.equals(true),
      );
      return result != null ? AIConfigModel.fromConfig(result) : null;
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 获取默认AI配置失败: $e', stackTrace: stackTrace);
      return null;
    }
  }

  /// 更新AI配置
  int updateAIConfig(AIConfigModel model) {
    try {
      model.updatedAt = DateTime.now();
      return save(model.config);
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 更新AI配置失败: $e', stackTrace: stackTrace);
      rethrow;
    }
  }

  /// 删除AI配置
  bool removeAIConfig(int id) {
    try {
      return remove(id);
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 删除AI配置失败: $e', stackTrace: stackTrace);
      return false;
    }
  }

  /// 设置指定功能类型的默认配置
  void setDefaultConfig(int configId, int functionType) {
    try {
      // 先取消该功能类型的所有默认配置
      final configList = getAIConfigsByFunctionType(functionType);
      for (var modelConfig in configList) {
        if (modelConfig.isDefault) {
          modelConfig.isDefault = false;
          updateAIConfig(modelConfig);
        }
      }

      // 设置新的默认配置
      final config = box.get(configId);
      if (config != null) {
        final modelConfig = AIConfigModel.fromConfig(config);
        modelConfig.isDefault = true;
        updateAIConfig(modelConfig);
      }
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 设置默认配置失败: $e', stackTrace: stackTrace);
    }
  }

  /// 初始化默认配置
  void initDefaultConfigs() {
    try {
      if (getAllAIConfigs().isEmpty) {
        // 添加通用配置
        final generalConfig = AIConfigModel(
          AIConfig(
            name: "通用配置",
            apiAddress: "https://api.openai.com/v1",
            apiToken: "",
            modelName: "gpt-4o-mini",
            functionType: 0,
            isDefault: true,
          ),
        );
        addAIConfig(generalConfig);

        // 添加文章分析配置
        final articleConfig = AIConfigModel(
          AIConfig(
            name: "文章分析",
            apiAddress: "",
            apiToken: "",
            modelName: "",
            functionType: 1,
            inheritFromGeneral: true,
            isDefault: true,
          ),
        );
        addAIConfig(articleConfig);

        // 添加书本解读配置
        final bookConfig = AIConfigModel(
          AIConfig(
            name: "书本解读",
            apiAddress: "",
            apiToken: "",
            modelName: "",
            functionType: 2,
            inheritFromGeneral: true,
            isDefault: true,
          ),
        );
        addAIConfig(bookConfig);

        // 添加日记总结配置
        final diaryConfig = AIConfigModel(
          AIConfig(
            name: "日记总结",
            apiAddress: "",
            apiToken: "",
            modelName: "",
            functionType: 3,
            inheritFromGeneral: true,
            isDefault: true,
          ),
        );
        addAIConfig(diaryConfig);
      }
    } catch (e, stackTrace) {
      logger.e('[AI配置存储库] 初始化默认配置失败: $e', stackTrace: stackTrace);
    }
  }
}
