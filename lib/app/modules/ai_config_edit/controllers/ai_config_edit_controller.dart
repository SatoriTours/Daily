import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// AI配置编辑控制器
///
/// 负责AI配置的编辑和创建操作，包括：
/// - 创建新配置
/// - 编辑现有配置
class AIConfigEditController extends GetxController {
  // MARK: - 可观察属性

  /// 预设的API地址和模型名称
  final List<Map<String, dynamic>> apiPresets = [
    {
      'name': 'OpenAI API',
      'apiAddress': 'https://api.openai.com/v1',
      'models': ['gpt-3.5-turbo', 'gpt-4', 'gpt-4-turbo', 'gpt-4-vision'],
    },
    {
      'name': '阿里云百炼',
      'apiAddress': 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      'models': ['deepseek-v3', 'qwen2.5-14b-instruct-1m', 'qwen-turbo-latest'],
    },
    {
      'name': 'Azure OpenAI',
      'apiAddress': 'https://{your-resource-name}.openai.azure.com',
      'models': ['gpt-35-turbo', 'gpt-4', 'gpt-4-32k'],
    },
    {
      'name': 'Anthropic API',
      'apiAddress': 'https://api.anthropic.com',
      'models': ['claude-3-opus', 'claude-3-sonnet', 'claude-3-haiku', 'claude-3-5-sonnet'],
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

  /// 当前编辑的配置ID
  int? currentEditingConfigId;

  /// 当前编辑的配置
  AIConfigModel? currentConfig;

  /// 选中的功能类型
  final RxInt selectedFunctionType = 0.obs;

  // MARK: - 控制器
  // 使用公开的控制器来供编辑页面访问
  late TextEditingController nameController;
  late TextEditingController apiAddressController;
  late TextEditingController apiTokenController;
  late TextEditingController modelNameController;

  /// 是否继承通用配置
  final RxBool inheritFromGeneralController = false.obs;

  // MARK: - 生命周期方法

  @override
  void onInit() {
    super.onInit();
    _initControllers();
    _initPresetListeners();
    _processArguments();
  }

  /// 初始化控制器
  void _initControllers() {
    nameController = TextEditingController();
    apiAddressController = TextEditingController();
    apiTokenController = TextEditingController();
    modelNameController = TextEditingController();
  }

  /// 初始化预设监听器
  void _initPresetListeners() {
    // 监听API预设变化
    ever(selectedApiPresetIndex, _handleApiPresetChange);

    // 监听模型索引变化
    ever(selectedModelIndex, _handleModelIndexChange);
  }

  /// 处理传递的参数
  void _processArguments() {
    if (Get.arguments != null) {
      final Map<String, dynamic> args = Get.arguments;

      // 编辑模式
      if (args.containsKey('config')) {
        _setupEditMode(args['config']);
      }
      // 新建模式
      else if (args.containsKey('functionType')) {
        _setupCreateMode(args['functionType']);
      }
    }
  }

  /// 设置编辑模式
  void _setupEditMode(AIConfigModel config) {
    isEditMode.value = true;
    currentConfig = config;
    currentEditingConfigId = config.id;
    selectedFunctionType.value = config.functionType;

    // 初始化状态
    _initializeFieldsFromConfig(config);
  }

  /// 设置新建模式
  void _setupCreateMode(int functionType) {
    isEditMode.value = false;
    currentConfig = null;
    currentEditingConfigId = null;
    selectedFunctionType.value = functionType;

    // 初始化相关状态
    selectedApiPresetIndex.value = 0;

    // 初始化可用模型列表
    if (apiPresets.isNotEmpty && apiPresets[0]['models'] is List) {
      availableModels.value = List<String>.from(apiPresets[0]['models']);
      selectedModelIndex.value = availableModels.isNotEmpty ? 0 : -1;
    } else {
      availableModels.clear();
      selectedModelIndex.value = -1;
    }

    // 设置默认值
    inheritFromGeneralController.value = (functionType != 0);

    // 初始化临时控制器
    nameController.text = AIConfigService.i.getFunctionTypeName(functionType);
    apiAddressController.text = apiPresets.isNotEmpty ? apiPresets[0]['apiAddress'] : "";
    apiTokenController.text = "";
    modelNameController.text = availableModels.isNotEmpty ? availableModels[0] : "";
  }

  /// 从配置中初始化字段
  void _initializeFieldsFromConfig(AIConfigModel config) {
    // 初始化状态
    availableModels.clear();
    selectedModelIndex.value = 0;

    // 初始化临时控制器
    nameController.text = config.name;
    apiAddressController.text = config.apiAddress;
    apiTokenController.text = config.apiToken;
    modelNameController.text = config.modelName;
    inheritFromGeneralController.value = config.inheritFromGeneral;

    // 查找API预设
    _setApiPresetFromConfig(config);
  }

  /// 处理API预设变化
  void _handleApiPresetChange(int index) {
    if (index < 0 || index >= apiPresets.length) {
      return;
    }

    // 更新API地址
    apiAddressController.text = apiPresets[index]['apiAddress'];

    // 更新可用模型列表
    if (index == apiPresets.length - 1) {
      // 自定义API
      availableModels.clear();
    } else if (apiPresets[index]['models'] is List) {
      // 有预设模型
      availableModels.value = List<String>.from(apiPresets[index]['models']);
      if (availableModels.isNotEmpty) {
        selectedModelIndex.value = 0;
        modelNameController.text = availableModels[0];
      }
    }
  }

  /// 处理模型索引变化
  void _handleModelIndexChange(int index) {
    if (availableModels.isEmpty || index < 0 || index >= availableModels.length) {
      return;
    }

    modelNameController.text = availableModels[index];
  }

  /// 当控制器被关闭时调用
  @override
  void onClose() {
    // 清理控制器
    nameController.dispose();
    apiAddressController.dispose();
    apiTokenController.dispose();
    modelNameController.dispose();

    // 清理可观察属性，避免内存泄漏
    availableModels.clear();

    super.onClose();
  }

  /// 保存当前编辑中的配置
  Future<bool> saveConfig() async {
    try {
      if (isEditMode.value && currentConfig != null) {
        // 编辑现有配置
        currentConfig!
          ..name = nameController.text
          ..apiAddress = apiAddressController.text
          ..apiToken = apiTokenController.text
          ..modelName = modelNameController.text
          ..inheritFromGeneral = inheritFromGeneralController.value;

        final success = await _updateConfig(currentConfig!);
        if (success) {
          UIUtils.showSuccess("更新配置成功");
          return true;
        }
      } else {
        // 创建新配置
        final config = AIConfigModel(
          AIConfig(
            name: nameController.text,
            apiAddress: apiAddressController.text,
            apiToken: apiTokenController.text,
            modelName: modelNameController.text,
            functionType: selectedFunctionType.value,
            inheritFromGeneral: inheritFromGeneralController.value,
          ),
        );

        final success = await _createConfig(config);
        if (success) {
          UIUtils.showSuccess("创建配置成功");
          return true;
        }
      }
      return false;
    } catch (e, stackTrace) {
      logger.e("[AI配置编辑控制器] 保存配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("保存配置失败: $e");
      return false;
    }
  }

  /// 创建新配置
  Future<bool> _createConfig(AIConfigModel config) async {
    try {
      // 新建配置
      final id = AIConfigRepository.addAIConfig(config);
      config.id = id;

      // 通知列表页刷新
      Get.back(result: {'action': 'created', 'config': config});
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置编辑控制器] 创建配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("创建配置失败: $e");
      return false;
    }
  }

  /// 更新配置
  Future<bool> _updateConfig(AIConfigModel config) async {
    try {
      // 更新配置
      AIConfigRepository.updateAIConfig(config);

      // 通知列表页刷新
      Get.back(result: {'action': 'updated', 'config': config});
      return true;
    } catch (e, stackTrace) {
      logger.e("[AI配置编辑控制器] 更新配置失败: $e", stackTrace: stackTrace);
      UIUtils.showError("更新配置失败: $e");
      return false;
    }
  }

  /// 从配置中设置API预设
  void _setApiPresetFromConfig(AIConfigModel config) {
    // 查找匹配的预设
    int presetIndex = apiPresets.indexWhere((preset) => preset['apiAddress'] == config.apiAddress);

    if (presetIndex != -1) {
      // 如果找到匹配的预设
      selectedApiPresetIndex.value = presetIndex;

      // 更新可用模型列表
      final presetModels = apiPresets[presetIndex]['models'];
      if (presetModels is List) {
        availableModels.value = List<String>.from(presetModels);

        // 查找模型
        if (availableModels.contains(config.modelName)) {
          selectedModelIndex.value = availableModels.indexOf(config.modelName);
        }
      }
    } else {
      // 如果没找到匹配的预设，使用自定义
      customApiAddress.value = config.apiAddress;
      selectedApiPresetIndex.value = apiPresets.length - 1;
    }
  }
}
