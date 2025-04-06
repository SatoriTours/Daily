import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// AI配置控制器
///
/// 负责管理AI配置的CRUD操作，包括：
/// - 加载配置列表
/// - 创建新配置
/// - 编辑现有配置
/// - 删除配置
/// - 设置默认配置
class AIConfigController extends GetxController {
  // MARK: - 可观察属性

  /// 配置列表
  final RxList<AIConfigModel> configs = <AIConfigModel>[].obs;

  /// 选中的功能类型
  final RxInt selectedFunctionType = 0.obs;

  /// 是否正在加载
  final RxBool isLoading = false.obs;

  /// 预设的API地址和模型名称
  final List<Map<String, dynamic>> apiPresets = [
    {
      'name': 'OpenAI API',
      'apiAddress': 'https://api.openai.com/v1',
      'models': ['gpt-3.5-turbo', 'gpt-4', 'gpt-4-turbo', 'gpt-4-vision'],
    },
    {
      'name': 'Azure OpenAI',
      'apiAddress': 'https://{your-resource-name}.openai.azure.com',
      'models': ['gpt-35-turbo', 'gpt-4', 'gpt-4-32k'],
    },
    {
      'name': 'Anthropic API',
      'apiAddress': 'https://api.anthropic.com',
      'models': ['claude-2', 'claude-instant-1', 'claude-3-opus', 'claude-3-sonnet'],
    },
    {'name': '自定义', 'apiAddress': '', 'models': []},
  ];

  /// 当前选择的API预设索引
  final RxInt selectedApiPresetIndex = 0.obs;

  /// 可用的模型列表（基于当前选择的API预设）
  final RxList<String> availableModels = <String>[].obs;

  /// 当前选择的模型索引
  final RxInt selectedModelIndex = 0.obs;

  /// 自定义API地址
  final RxString customApiAddress = ''.obs;

  /// 当前是否处于编辑模式（而非新建模式）
  final RxBool isEditMode = false.obs;

  /// 当前编辑的配置
  AIConfigModel? currentEditingConfig;

  // MARK: - 控制器
  // 使用公开的控制器来供编辑页面访问
  TextEditingController tempNameController = TextEditingController();
  TextEditingController tempApiAddressController = TextEditingController();
  TextEditingController tempApiTokenController = TextEditingController();
  TextEditingController tempModelNameController = TextEditingController();

  /// 是否继承通用配置
  final RxBool inheritFromGeneralController = false.obs;

  // MARK: - 生命周期方法

  @override
  void onInit() {
    super.onInit();
    loadConfigs();
    _initPresetListeners();
  }

  /// 初始化预设监听器
  void _initPresetListeners() {
    // 监听API预设变化
    ever(selectedApiPresetIndex, _handleApiPresetChange);

    // 监听模型索引变化
    ever(selectedModelIndex, _handleModelIndexChange);
  }

  /// 处理API预设变化
  void _handleApiPresetChange(int index) {
    if (index < 0 || index >= apiPresets.length) {
      return;
    }

    // 更新API地址
    tempApiAddressController.text = apiPresets[index]['apiAddress'];

    // 更新可用模型列表
    if (index == apiPresets.length - 1) {
      // 自定义API
      availableModels.clear();
    } else if (apiPresets[index]['models'] is List) {
      // 有预设模型
      availableModels.value = List<String>.from(apiPresets[index]['models']);
      if (availableModels.isNotEmpty) {
        selectedModelIndex.value = 0;
        tempModelNameController.text = availableModels[0];
      }
    }
  }

  /// 处理模型索引变化
  void _handleModelIndexChange(int index) {
    if (availableModels.isEmpty || index < 0 || index >= availableModels.length) {
      return;
    }

    tempModelNameController.text = availableModels[index];
  }

  /// 当控制器被关闭时调用
  @override
  void onClose() {
    // 清理控制器
    tempNameController.dispose();
    tempApiAddressController.dispose();
    tempApiTokenController.dispose();
    tempModelNameController.dispose();

    // 清理可观察属性，避免内存泄漏
    availableModels.clear();
    configs.clear();

    super.onClose();
  }

  // MARK: - 功能方法

  /// 加载配置列表
  ///
  /// 从数据库加载所有AI配置，并更新[configs]列表。
  /// 如果加载失败，会显示错误提示。
  void loadConfigs() {
    isLoading.value = true;
    try {
      final configsList = AIConfigRepository.getAllAIConfigs();
      configs.value = configsList;
      logger.i("[AI配置控制器] 加载配置列表成功: ${configsList.length}个配置");
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 加载配置列表失败: $e", stackTrace: stackTrace);
      UIUtils.showError("加载配置失败: $e");
    } finally {
      isLoading.value = false;
    }
  }

  /// 获取特定功能类型的配置
  ///
  /// [type] 功能类型ID
  /// 返回指定类型的配置列表
  List<AIConfigModel> getConfigsByType(int type) {
    return configs.where((config) => config.functionType == type).toList();
  }

  /// 保存配置到列表
  ///
  /// 当从编辑页面返回时，更新本地列表数据
  void updateConfigsList(dynamic result) {
    if (result is Map<String, dynamic>) {
      final action = result['action'];
      final config = result['config'] as AIConfigModel?;

      if (config != null) {
        if (action == 'created') {
          configs.add(config);
        } else if (action == 'updated') {
          final index = configs.indexWhere((c) => c.id == config.id);
          if (index >= 0) {
            configs[index] = config;
          }
        }
        configs.refresh();
      }
    }
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

      final result = AIConfigRepository.removeAIConfig(config.id);
      if (result) {
        configs.removeWhere((c) => c.id == config.id);
        configs.refresh();
      }
      return result;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 删除配置失败: $e", stackTrace: stackTrace);
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
      AIConfigRepository.setDefaultConfig(config.id, config.functionType);
      loadConfigs();
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 设置默认配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("设置默认配置失败: $e");
      return false;
    }
  }

  /// 创建新配置
  Future<void> createNewConfig() async {
    // 设置当前功能类型
    final result = await Get.toNamed(Routes.aiConfigEdit, arguments: {'functionType': selectedFunctionType.value});

    // 处理返回结果，更新列表
    updateConfigsList(result);
  }

  /// 编辑配置
  ///
  /// [config] 要编辑的配置
  Future<void> editConfig(AIConfigModel config) async {
    // 传递配置到编辑页面
    final result = await Get.toNamed(Routes.aiConfigEdit, arguments: {'config': config});

    // 处理返回结果，更新列表
    updateConfigsList(result);
  }

  /// 获取类型图标
  IconData getTypeIcon(int type) {
    switch (type) {
      case 0:
        return Icons.settings;
      case 1:
        return Icons.article;
      case 2:
        return Icons.book;
      case 3:
        return Icons.edit_note;
      default:
        return Icons.settings;
    }
  }

  /// 克隆配置
  Future<void> cloneConfig(AIConfigModel config) async {
    try {
      // 克隆配置
      final newConfig = config.clone();

      // 保存配置
      final id = AIConfigRepository.addAIConfig(newConfig);
      newConfig.id = id;
      configs.add(newConfig);
      configs.refresh();

      UIUtils.showSuccess("克隆配置成功");
    } catch (e, stackTrace) {
      logger.e("[AI配置控制器] 克隆配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("克隆配置失败: $e");
    }
  }
}
