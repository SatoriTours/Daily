import 'package:daily_satori/app_exports.dart';

/// AI配置控制器
///
/// 负责管理AI配置页面的UI状态和用户交互
/// 数据管理由AIConfigStateService负责
class AIConfigController extends BaseController {
  /// 构造函数
  AIConfigController(super._appStateService, this._aiConfigStateService);

  /// 状态服务
  final AIConfigStateService _aiConfigStateService;

  /// 获取AI配置列表
  RxList<AIConfigModel> get configs => _aiConfigStateService.configs;

  /// 获取加载状态
  @override
  RxBool get isLoading => _aiConfigStateService.isLoading;

  // ========================================================================
  // 属性
  // ========================================================================

  /// 编辑类型选择
  final RxInt selectedFunctionType = 0.obs;

  /// API预设配置
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
      'models': ['claude-2', 'claude-instant-1', 'claude-3-opus'],
    },
    {'name': 'Custom', 'apiAddress': '', 'models': []},
  ];

  /// API预设索引
  final RxInt selectedApiPresetIndex = 0.obs;

  /// 可用模型列表
  final RxList<String> availableModels = <String>[].obs;

  /// 模型选择索引
  final RxInt selectedModelIndex = 0.obs;

  /// 自定义API地址
  final RxString customApiAddress = ''.obs;

  /// 编辑模式
  final RxBool isEditMode = false.obs;

  /// 当前编辑的配置
  AIConfigModel? currentEditingConfig;

  /// 临时控制器
  TextEditingController tempNameController = TextEditingController();
  TextEditingController tempApiAddressController = TextEditingController();
  TextEditingController tempModelNameController = TextEditingController();
  TextEditingController tempApiTokenController = TextEditingController();

  /// 是否从通用设置继承
  final RxBool inheritFromGeneralController = false.obs;

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void onInit() {
    super.onInit();
    _initPresetListeners();
    _aiConfigStateService.loadConfigs();
  }

  /// 初始化预设监听器
  void _initPresetListeners() {
    ever(selectedApiPresetIndex, _handleApiPresetChange);
    ever(selectedModelIndex, _handleModelIndexChange);
  }

  // ========================================================================
  // UI事件处理
  // ========================================================================

  /// 创建新配置
  Future<void> createNewConfig() async {
    logger.i('[AIConfig] 创建新配置');
    final result = await Get.toNamed(Routes.aiConfigEdit, arguments: {'functionType': selectedFunctionType.value});
    _handleEditResult(result);
  }

  /// 编辑配置
  Future<void> editConfig(AIConfigModel config) async {
    logger.i('[AIConfig] 编辑配置: ${config.name}');
    final result = await Get.toNamed(Routes.aiConfigEdit, arguments: {'aiConfig': config});
    _handleEditResult(result);
  }

  /// 删除配置
  Future<bool> deleteConfig(AIConfigModel config) async {
    logger.i('[AIConfig] 删除配置: ${config.name}');
    final result = await _aiConfigStateService.deleteConfig(config);
    return result;
  }

  /// 设为默认配置
  Future<bool> setAsDefault(AIConfigModel config) async {
    logger.i('[AIConfig] 设置默认配置: ${config.name}');
    final result = await _aiConfigStateService.setAsDefault(config);
    return result;
  }

  /// 克隆配置
  Future<void> cloneConfig(AIConfigModel config) async {
    logger.i('[AIConfig] 克隆配置: ${config.name}');
    await _aiConfigStateService.cloneConfig(config);
  }

  /// 处理编辑返回结果
  Future<void> _handleEditResult(dynamic result) async {
    if (result is Map<String, dynamic>) {
      final action = result['action'];
      final config = result['config'] as AIConfigModel?;
      if (config != null) {
        if (action == 'created') {
          _aiConfigStateService.addConfigToList(config);
          logger.i("AI配置列表：添加新配置 ${config.name}");
        } else if (action == 'updated') {
          _aiConfigStateService.updateConfigInList(config);
          logger.i("AI配置列表：更新配置 ${config.name}");
        }
        // 确保UI更新
        configs.refresh();
      }
    }
  }

  /// 处理API预设变更
  void _handleApiPresetChange(int index) {
    if (index < 0 || index >= apiPresets.length) return;

    final preset = apiPresets[index];
    tempApiAddressController.text = preset['apiAddress'];
    tempModelNameController.text = preset['name'];
    availableModels.value = List<String>.from(preset['models']);
    selectedModelIndex.value = 0;
    tempModelNameController.text = availableModels.isNotEmpty ? availableModels[0] : '';

    logger.i('[AIConfig] 选择API预设: ${preset['name']}');
  }

  /// 处理模型索引变更
  void _handleModelIndexChange(int index) {
    if (index < 0 || index >= availableModels.length) return;

    tempModelNameController.text = availableModels[index];
    selectedModelIndex.value = index;

    logger.i('[AIConfig] 选择模型: ${availableModels[index]}');
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 获取配置类型图标
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
}
