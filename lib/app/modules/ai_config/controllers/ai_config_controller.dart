import 'package:daily_satori/app_exports.dart';

/// AI配置控制器
///
/// 负责管理AI配置页面的UI状态和用户交互
/// 数据管理由AIConfigStateService负责
class AIConfigController extends GetxController {
  // MARK: - 状态服务

  late final AIConfigStateService _aiConfigStateService;

  // MARK: - 数据引用

  RxList<AIConfigModel> get configs => _aiConfigStateService.configs;
  RxBool get isLoading => _aiConfigStateService.isLoading;

  // MARK: - UI状态

  final RxInt selectedFunctionType = 0.obs;

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
    {'name': 'Custom', 'apiAddress': '', 'models': []},
  ];

  final RxInt selectedApiPresetIndex = 0.obs;
  final RxList<String> availableModels = <String>[].obs;
  final RxInt selectedModelIndex = 0.obs;
  final RxString customApiAddress = ''.obs;
  final RxBool isEditMode = false.obs;

  AIConfigModel? currentEditingConfig;

  TextEditingController tempNameController = TextEditingController();
  TextEditingController tempApiAddressController = TextEditingController();
  TextEditingController tempApiTokenController = TextEditingController();
  TextEditingController tempModelNameController = TextEditingController();

  final RxBool inheritFromGeneralController = false.obs;

  @override
  void onInit() {
    super.onInit();
    _initStateService();
    _aiConfigStateService.loadConfigs();
    _initPresetListeners();
  }

  void _initStateService() {
    _aiConfigStateService = Get.find<AIConfigStateService>();
  }

  void _initPresetListeners() {
    ever(selectedApiPresetIndex, _handleApiPresetChange);
    ever(selectedModelIndex, _handleModelIndexChange);
  }

  void _handleApiPresetChange(int index) {
    if (index < 0 || index >= apiPresets.length) return;
    tempApiAddressController.text = apiPresets[index]['apiAddress'];
    if (index == apiPresets.length - 1) {
      availableModels.clear();
    } else if (apiPresets[index]['models'] is List) {
      availableModels.value = List<String>.from(apiPresets[index]['models']);
      if (availableModels.isNotEmpty) {
        selectedModelIndex.value = 0;
        tempModelNameController.text = availableModels[0];
      }
    }
  }

  void _handleModelIndexChange(int index) {
    if (availableModels.isEmpty || index < 0 || index >= availableModels.length) return;
    tempModelNameController.text = availableModels[index];
  }

  @override
  void onClose() {
    tempNameController.dispose();
    tempApiAddressController.dispose();
    tempApiTokenController.dispose();
    tempModelNameController.dispose();
    availableModels.clear();
    super.onClose();
  }

  void loadConfigs() {
    _aiConfigStateService.loadConfigs();
  }

  List<AIConfigModel> getConfigsByType(int type) {
    return _aiConfigStateService.getConfigsByType(type);
  }

  void updateConfigsList(dynamic result) {
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

  Future<bool> deleteConfig(AIConfigModel config) async {
    return await _aiConfigStateService.deleteConfig(config);
  }

  Future<bool> setAsDefault(AIConfigModel config) async {
    return await _aiConfigStateService.setAsDefault(config);
  }

  Future<void> cloneConfig(AIConfigModel config) async {
    await _aiConfigStateService.cloneConfig(config);
  }

  Future<void> createNewConfig() async {
    final result = await Get.toNamed(Routes.aiConfigEdit, arguments: {'functionType': selectedFunctionType.value});
    updateConfigsList(result);
  }

  Future<void> editConfig(AIConfigModel config) async {
    final result = await Get.toNamed(Routes.aiConfigEdit, arguments: {'aiConfig': config});

    // 处理编辑返回结果
    if (result != null) {
      updateConfigsList(result);
    } else {
      // 如果没有返回结果，可能是用户取消了编辑，但为了确保数据最新，重新加载配置
      await _aiConfigStateService.loadConfigs();
    }
  }

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
