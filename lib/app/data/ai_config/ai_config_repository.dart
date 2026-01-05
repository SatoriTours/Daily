import 'package:daily_satori/app/data/ai_config/ai_config_model.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/data/base/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// AI配置存储库
class AIConfigRepository extends BaseRepository<AIConfig, AIConfigModel> {
  // 私有构造函数
  AIConfigRepository._();

  static final i = AIConfigRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  AIConfigModel toModel(AIConfig entity) {
    return AIConfigModel(entity);
  }

  // toEntity 已由父类提供默认实现，无需重写

  // ==================== 业务方法 ====================

  /// 根据功能类型获取AI配置
  List<AIConfigModel> getAIConfigsByFunctionType(int functionType) {
    return findByCondition(AIConfig_.functionType.equals(functionType));
  }

  /// 获取通用配置
  AIConfigModel? getGeneralConfig() {
    return findFirstByCondition(AIConfig_.functionType.equals(0));
  }

  /// 根据功能类型获取默认AI配置
  AIConfigModel? getDefaultAIConfigByFunctionType(int functionType) {
    return findFirstByCondition(AIConfig_.functionType.equals(functionType) & AIConfig_.isDefault.equals(true));
  }

  /// 更新AI配置(设置更新时间)
  @override
  int save(AIConfigModel model) {
    model.entity.updatedAt = DateTime.now();
    return super.save(model);
  }

  /// 设置指定功能类型的默认配置
  void setDefaultConfig(int configId, int functionType) {
    // 先取消该功能类型的所有默认配置
    final configList = getAIConfigsByFunctionType(functionType);
    for (var modelConfig in configList) {
      if (modelConfig.isDefault) {
        modelConfig.isDefault = false;
        save(modelConfig);
      }
    }

    // 设置新的默认配置
    final config = box.get(configId);
    if (config != null) {
      final modelConfig = AIConfigModel(config);
      modelConfig.isDefault = true;
      save(modelConfig);
    }
  }

  /// 初始化默认配置
  void initDefaultConfigs() {
    if (all().isEmpty) {
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
      save(generalConfig);

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
      save(articleConfig);

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
      save(bookConfig);

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
      save(diaryConfig);
    }
  }
}
