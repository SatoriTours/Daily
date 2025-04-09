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
  final List<AiModel> apiPresets;

  /// 表单控制器
  late final TextEditingController nameController;
  late final TextEditingController apiAddressController;
  late final TextEditingController apiTokenController;
  late final TextEditingController modelNameController;

  /// 响应式变量
  final _selectedApiPresetIndex = 0.obs;
  final _isEditMode = false.obs;
  final _modelName = ''.obs;

  /// Getters for reactive variables
  int get selectedApiPresetIndex => _selectedApiPresetIndex.value;
  bool get isEditMode => _isEditMode.value;
  String get modelName => _modelName.value;

  /// 是否显示自定义API地址
  bool get isCustomApiAddress => selectedApiPresetIndex == apiPresets.length - 1;

  /// 获取当前选中API预设的可用模型列表
  List<String> get availableModels {
    if (selectedApiPresetIndex == -1) return [];
    final models = apiPresets[selectedApiPresetIndex].models;
    return models;
  }

  /// 构造函数
  AIConfigEditController() : aiConfig = Get.arguments['aiConfig'], apiPresets = PluginService.i.getAiModels();

  @override
  void onInit() {
    super.onInit();
    _initTextControllers();
    _initializeFromConfig();
    _isEditMode.value = aiConfig != null;
  }

  /// 初始化控制器
  void _initTextControllers() {
    nameController = TextEditingController();
    apiAddressController = TextEditingController();
    apiTokenController = TextEditingController();
    modelNameController = TextEditingController();
  }

  /// 从配置初始化表单
  void _initializeFromConfig() {
    if (aiConfig == null) return;

    nameController.text = aiConfig!.name;
    apiTokenController.text = aiConfig!.apiToken;

    updateApiAddressByUrl(aiConfig!.apiAddress);
    updateModelName(aiConfig!.modelName);
  }

  /// 更新API地址
  void updateApiAddressByUrl(String url) {
    final index = apiPresets.indexWhere((preset) => preset.apiAddress == url);

    if (index != -1) {
      _selectedApiPresetIndex.value = index;
      apiAddressController.text = apiPresets[index].apiAddress;
    }
  }

  /// 更新API地址
  void updateApiAddress(int index) {
    if (index < 0 || index >= apiPresets.length) return;

    _selectedApiPresetIndex.value = index;
    apiAddressController.text = apiPresets[index].apiAddress;
  }

  /// 更新模型名称
  void updateModelName(String modelName) {
    modelNameController.text = modelName;
    _modelName.value = modelName;
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

      // 如果是编辑模式，直接更新现有配置的属性
      // 如果是新建模式，上面已经创建了包含所有属性的对象，不需要再次设置
      if (isEditMode) {
        configToSave
          ..name = nameController.text
          ..apiAddress = apiAddressController.text
          ..apiToken = apiTokenController.text
          ..modelName = modelNameController.text
          ..inheritFromGeneral = true;
      }

      if (isEditMode) {
        // 更新现有配置
        AIConfigRepository.updateAIConfig(configToSave);
      } else {
        // 创建新配置
        final id = AIConfigRepository.addAIConfig(configToSave);
        configToSave.id = id;
      }
      Get.back();
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
