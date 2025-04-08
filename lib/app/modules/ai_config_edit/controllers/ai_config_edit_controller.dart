import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// AI配置编辑控制器
///
/// 负责AI配置的编辑和创建操作，包括：
/// - 创建新配置
/// - 编辑现有配置
class AIConfigEditController extends GetxController {
  /// 当前编辑的配置
  final AIConfigModel? aiConfig;

  /// API预设列表
  final List<Map<String, dynamic>> apiPresets;

  /// 表单控制器
  late final TextEditingController nameController;
  late final TextEditingController apiAddressController;
  late final TextEditingController apiTokenController;
  late final TextEditingController modelNameController;

  /// 响应式变量
  final _selectedApiPresetIndex = 0.obs;
  final _isEditMode = false.obs;

  /// Getters for reactive variables
  int get selectedApiPresetIndex => _selectedApiPresetIndex.value;
  bool get isEditMode => _isEditMode.value;

  /// 是否显示自定义API地址
  bool get isCustomApiAddress => selectedApiPresetIndex == apiPresets.length - 1;

  /// 获取当前选中API预设的可用模型列表
  List<String> get availableModels {
    if (selectedApiPresetIndex == -1) return [];
    final models = apiPresets[selectedApiPresetIndex]['models'];
    return models is List ? List<String>.from(models) : [];
  }

  /// 构造函数
  AIConfigEditController()
    : aiConfig = Get.arguments['aiConfig'],
      apiPresets = [
        ...PluginService.i.getApiPresets().map(
          (preset) => {'name': preset.name, 'apiAddress': preset.apiAddress, 'models': preset.models},
        ),
      ];

  @override
  void onInit() {
    super.onInit();
    _initControllers();
    _initializeFromConfig();
    _isEditMode.value = aiConfig != null;
  }

  /// 初始化控制器
  void _initControllers() {
    nameController = TextEditingController();
    apiAddressController = TextEditingController();
    apiTokenController = TextEditingController();
    modelNameController = TextEditingController();
  }

  /// 从配置初始化表单
  void _initializeFromConfig() {
    if (aiConfig == null) return;

    nameController.text = aiConfig!.name;
    apiAddressController.text = aiConfig!.apiAddress;
    apiTokenController.text = aiConfig!.apiToken;
    modelNameController.text = aiConfig!.modelName;

    // 设置当前选中的API预设
    final index = apiPresets.indexWhere((preset) => preset['apiAddress'] == aiConfig!.apiAddress);
    if (index != -1) {
      _selectedApiPresetIndex.value = index;
    }
  }

  /// 更新API地址
  void updateApiAddress(int index) {
    if (index < 0 || index >= apiPresets.length) return;

    _selectedApiPresetIndex.value = index;
    apiAddressController.text = apiPresets[index]['apiAddress'];

    // 如果有预设模型且当前模型不在列表中，选择第一个模型
    final models = availableModels;
    if (models.isNotEmpty && !models.contains(modelNameController.text)) {
      modelNameController.text = models[0];
    }
  }

  /// 更新模型名称
  void updateModelName(String modelName) {
    modelNameController.text = modelName;
  }

  /// 保存配置
  Future<bool> saveConfig() async {
    try {
      final configToSave =
          isEditMode
              ? aiConfig!
              : AIConfigModel(
                AIConfig(
                  name: nameController.text,
                  apiAddress: apiAddressController.text,
                  apiToken: apiTokenController.text,
                  modelName: modelNameController.text,
                  functionType: 0, // 默认为通用配置
                  inheritFromGeneral: true,
                ),
              );

      // 更新配置
      configToSave
        ..name = nameController.text
        ..apiAddress = apiAddressController.text
        ..apiToken = apiTokenController.text
        ..modelName = modelNameController.text
        ..inheritFromGeneral = true;

      if (isEditMode) {
        // 更新现有配置
        AIConfigRepository.updateAIConfig(configToSave);
        Get.back(result: {'action': 'updated', 'config': configToSave});
      } else {
        // 创建新配置
        final id = AIConfigRepository.addAIConfig(configToSave);
        configToSave.id = id;
        Get.back(result: {'action': 'created', 'config': configToSave});
      }

      return true;
    } catch (e) {
      Get.snackbar('错误', '保存配置失败: $e', snackPosition: SnackPosition.top, backgroundColor: Colors.red.withAlpha(200));
      return false;
    }
  }

  @override
  void onClose() {
    nameController.dispose();
    apiAddressController.dispose();
    apiTokenController.dispose();
    modelNameController.dispose();
    super.onClose();
  }
}
