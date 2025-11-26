import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/objectbox/ai_config.dart';

/// AI配置编辑控制器
///
/// 负责AI配置的编辑和创建操作，包括：
/// - 创建新配置
/// - 编辑现有配置
class AIConfigEditController extends BaseController {
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
  final _apiToken = ''.obs;
  final _apiAddress = ''.obs;
  final _name = ''.obs;
  final _inheritFromGeneral = false.obs;
  final _initialized = false.obs;

  /// Getters for reactive variables
  int get selectedApiPresetIndex => _selectedApiPresetIndex.value;
  bool get isEditMode => _isEditMode.value;
  String get modelName => _modelName.value;
  String get apiToken => _apiToken.value;
  String get apiAddress => _apiAddress.value;
  String get name => _name.value;
  bool get inheritFromGeneral => _inheritFromGeneral.value;
  @override
  bool get isInitialized => _initialized.value;

  /// 是否显示自定义API地址
  bool get isCustomApiAddress => selectedApiPresetIndex == apiPresets.length - 1;

  /// 获取当前选中API预设的可用模型列表
  List<String> get availableModels {
    if (selectedApiPresetIndex == -1) return [];
    final models = apiPresets[selectedApiPresetIndex].models;
    return models;
  }

  /// 检查表单是否有效
  bool get isFormValid {
    if (!isInitialized) return false;

    // 基础验证：配置名称不能为空
    if (name.trim().isEmpty) return false;

    // 如果是继承模式，只需要验证配置名称
    if (inheritFromGeneral) return true;

    // 独立模式：需要验证API相关字段
    return modelName.trim().isNotEmpty && apiToken.trim().isNotEmpty;
  }

  /// 判断是否为系统预设配置类型（不可修改名称）
  bool get isSystemConfig {
    if (aiConfig == null) return false;
    return aiConfig!.functionType >= 0 && aiConfig!.functionType <= 3;
  }

  /// 判断是否为特殊配置类型（可以继承通用配置）
  bool get isSpecialConfig {
    if (aiConfig == null) return false;
    return aiConfig!.functionType >= 1 && aiConfig!.functionType <= 3; // 文章总结、书本解读、日记总结
  }

  /// 设置继承模式
  void setInheritFromGeneral(bool value) {
    logger.i('设置继承模式: $value');

    if (value) {
      // 切换到继承模式：清空API相关字段
      apiAddressController.clear();
      apiTokenController.clear();
      modelNameController.clear();
      _apiAddress.value = '';
      _apiToken.value = '';
      _modelName.value = '';

      // 重置API提供商选择
      _selectedApiPresetIndex.value = 0;
      availableModels.clear();
    } else {
      // 切换到独立模式：设置默认值
      _apiAddress.value = '';
      _apiToken.value = '';
      _modelName.value = '';
    }

    _inheritFromGeneral.value = value;
  }

  /// 获取页面标题（如果是系统配置则显示配置名称，否则显示编辑/新建配置）
  String get pageTitle {
    if (isSystemConfig && aiConfig != null) {
      return aiConfig!.name;
    }
    return isEditMode ? '编辑配置' : '新建配置';
  }

  /// 构造函数
  AIConfigEditController(super._appStateService)
    : aiConfig = Get.arguments?['aiConfig'],
      apiPresets = PluginService.i.getAiModels();

  @override
  void onInit() {
    super.onInit();
    _initTextControllers();
    _initializeFromConfig();
    _isEditMode.value = aiConfig != null;
    _initialized.value = true;
  }

  /// 初始化控制器
  void _initTextControllers() {
    nameController = TextEditingController();
    apiAddressController = TextEditingController();
    apiTokenController = TextEditingController();
    modelNameController = TextEditingController();

    // 添加监听器以更新响应式变量
    nameController.addListener(() {
      _name.value = nameController.text;
    });

    apiTokenController.addListener(() {
      _apiToken.value = apiTokenController.text;
    });

    modelNameController.addListener(() {
      _modelName.value = modelNameController.text;
    });
  }

  /// 从配置初始化表单
  void _initializeFromConfig() {
    if (aiConfig == null) return;

    nameController.text = aiConfig!.name;
    _name.value = aiConfig!.name;
    apiTokenController.text = aiConfig!.apiToken;
    _apiToken.value = aiConfig!.apiToken;

    updateApiAddressByUrl(aiConfig!.apiAddress);
    updateModelName(aiConfig!.modelName);

    // 根据实际配置内容判断是否继承通用配置
    // 如果API地址为空，说明继承了通用配置
    final isActuallyInheriting = aiConfig!.apiAddress.isEmpty;
    _inheritFromGeneral.value = isActuallyInheriting;

    logger.i('初始化配置: ${aiConfig!.name}, API地址: "${aiConfig!.apiAddress}", 继承状态: $isActuallyInheriting');
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

    // 重置模型名称为第一个可用模型或空
    if (availableModels.isNotEmpty) {
      updateModelName(availableModels[0]);
    } else {
      updateModelName('');
    }

    // 如果不是自定义API地址，清空API令牌
    if (!isCustomApiAddress) {
      apiTokenController.text = '';
      _apiToken.value = '';
    }
  }

  /// 更新模型名称
  void updateModelName(String modelName) {
    modelNameController.text = modelName;
    _modelName.value = modelName;
  }

  /// 保存配置
  Future<bool> saveConfig() async {
    // 验证表单
    if (!isFormValid) {
      UIUtils.showError('请填写完整的配置信息', title: '验证失败');
      return false;
    }

    try {
      final configToSave = isEditMode
          ? aiConfig!
          : AIConfigModel(
              AIConfig(
                name: nameController.text,
                apiAddress: inheritFromGeneral ? '' : apiAddressController.text,
                apiToken: inheritFromGeneral ? '' : apiTokenController.text,
                modelName: inheritFromGeneral ? '' : modelNameController.text,
                functionType: 0, // 默认为通用配置
                inheritFromGeneral: inheritFromGeneral,
              ),
            );

      // 如果是编辑模式，直接更新现有配置的属性
      // 如果是新建模式，上面已经创建了包含所有属性的对象，不需要再次设置
      if (isEditMode) {
        configToSave
          ..name = nameController.text
          ..inheritFromGeneral = inheritFromGeneral;

        // 根据继承模式设置API相关字段
        if (inheritFromGeneral) {
          // 继承模式：清空API相关字段
          configToSave
            ..apiAddress = ''
            ..apiToken = ''
            ..modelName = '';
        } else {
          // 独立模式：保留用户设置的API相关字段
          configToSave
            ..apiAddress = apiAddressController.text
            ..apiToken = apiTokenController.text
            ..modelName = modelNameController.text;
        }
      }

      if (isEditMode) {
        // 更新现有配置
        AIConfigRepository.i.save(configToSave);
      } else {
        // 创建新配置
        final id = AIConfigRepository.i.save(configToSave);
        configToSave.entity.id = id;
      }
      // 返回结果给调用方，以便更新列表
      Get.back(result: {'action': isEditMode ? 'updated' : 'created', 'config': configToSave});
      return true;
    } catch (e) {
      UIUtils.showError('保存配置失败: $e', title: '错误');
      return false;
    }
  }

  /// 重置表单到原始状态
  void resetConfig() {
    if (aiConfig == null) {
      // 如果是新建模式，清空所有字段
      nameController.clear();
      apiTokenController.clear();
      apiAddressController.clear();
      modelNameController.clear();
      _selectedApiPresetIndex.value = 0;
      _modelName.value = '';
      _apiToken.value = '';
      _name.value = '';
    } else {
      // 如果是编辑模式，恢复到原始配置
      _initializeFromConfig();
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
