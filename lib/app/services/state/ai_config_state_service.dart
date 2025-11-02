import 'package:daily_satori/app_exports.dart';

/// AI配置状态服务
///
/// 作为AI配置数据的唯一数据源，管理AI配置列表和相关状态
class AIConfigStateService extends GetxService {
  // MARK: - 可观察属性

  /// 配置列表
  final RxList<AIConfigModel> configs = <AIConfigModel>[].obs;

  /// 是否正在加载
  final RxBool isLoading = false.obs;

  // MARK: - 生命周期方法

  @override
  void onInit() {
    super.onInit();
    logger.i('AIConfigStateService 初始化');
  }

  @override
  void onClose() {
    configs.clear();
    logger.i('AIConfigStateService 已关闭');
    super.onClose();
  }

  // MARK: - 数据加载

  /// 加载配置列表
  ///
  /// 从数据库加载所有AI配置
  Future<void> loadConfigs() async {
    isLoading.value = true;
    try {
      final configsList = AIConfigRepository.i.all();
      configs.value = configsList;
      logger.i("[AI配置状态服务] 加载配置列表成功: ${configsList.length}个配置");
    } catch (e, stackTrace) {
      logger.e("[AI配置状态服务] 加载配置列表失败: $e", stackTrace: stackTrace);
      UIUtils.showError("加载配置失败: $e");
    } finally {
      isLoading.value = false;
    }
  }

  // MARK: - 数据查询

  /// 获取特定功能类型的配置
  ///
  /// [type] 功能类型ID
  /// 返回指定类型的配置列表
  List<AIConfigModel> getConfigsByType(int type) {
    return configs.where((config) => config.functionType == type).toList();
  }

  // MARK: - CRUD操作

  /// 添加配置到列表
  ///
  /// [config] 要添加的配置
  void addConfigToList(AIConfigModel config) {
    configs.add(config);
  }

  /// 更新列表中的配置
  ///
  /// [config] 要更新的配置
  void updateConfigInList(AIConfigModel config) {
    final index = configs.indexWhere((c) => c.id == config.id);
    if (index >= 0) {
      configs[index] = config;
    }
  }

  /// 从列表中移除配置
  ///
  /// [configId] 要移除的配置ID
  void removeConfigFromList(int configId) {
    configs.removeWhere((c) => c.id == configId);
  }

  /// 删除配置
  ///
  /// [config] 要删除的配置
  /// 返回是否删除成功
  Future<bool> deleteConfig(AIConfigModel config) async {
    try {
      // 不允许删除最后一个指定类型的配置
      final typeConfigs = getConfigsByType(config.functionType);
      if (typeConfigs.length <= 1 && config.functionType != 0) {
        UIUtils.showError("不能删除最后一个${AIConfigService.i.getFunctionTypeName(config.functionType)}配置");
        return false;
      }

      final result = AIConfigRepository.i.remove(config.id);
      if (result) {
        removeConfigFromList(config.id);
      }
      return result;
    } catch (e, stackTrace) {
      logger.e("[AI配置状态服务] 删除配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("删除配置失败: $e");
      return false;
    }
  }

  /// 设置默认配置
  ///
  /// [config] 要设置为默认的配置
  /// 返回是否设置成功
  Future<bool> setAsDefault(AIConfigModel config) async {
    try {
      AIConfigRepository.i.setDefaultConfig(config.id, config.functionType);
      await loadConfigs();
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置状态服务] 设置默认配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("设置默认配置失败: $e");
      return false;
    }
  }

  /// 克隆配置
  ///
  /// [config] 要克隆的配置
  /// 返回是否克隆成功
  Future<bool> cloneConfig(AIConfigModel config) async {
    try {
      // 克隆配置
      final newConfig = config.clone();

      // 保存配置
      final id = AIConfigRepository.i.save(newConfig);
      newConfig.entity.id = id;
      addConfigToList(newConfig);

      UIUtils.showSuccess("克隆配置成功");
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置状态服务] 克隆配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("克隆配置失败: $e");
      return false;
    }
  }
}
