import 'package:daily_satori/app_exports.dart';
import '../models/ai_config_types.dart';

/// AI配置控制器
///
/// 负责管理AI配置页面的UI状态和用户交互
/// 数据管理由AIConfigStateService负责
class AIConfigController extends BaseController {
  // ========================================================================
  // 构造函数
  // ========================================================================

  AIConfigController(super._appStateService, this._aiConfigStateService);

  // ========================================================================
  // 依赖服务
  // ========================================================================

  /// 状态服务
  final AIConfigStateService _aiConfigStateService;

  // ========================================================================
  // 属性
  // ========================================================================

  /// 获取AI配置列表
  RxList<AIConfigModel> get configs => _aiConfigStateService.configs;

  /// 获取加载状态
  @override
  RxBool get isLoading => _aiConfigStateService.isLoading;

  /// 编辑类型选择
  final RxInt selectedFunctionType = 0.obs;

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void onInit() {
    super.onInit();
    logger.d('[AIConfigController] 初始化');
    _aiConfigStateService.loadConfigs();
  }

  // ========================================================================
  // 公共方法 - 配置操作
  // ========================================================================

  /// 创建新配置
  Future<void> createNewConfig() async {
    logger.i('[AIConfigController] 创建新配置');
    final result = await Get.toNamed(
      Routes.aiConfigEdit,
      arguments: {'functionType': selectedFunctionType.value},
    );
    _handleEditResult(result);
  }

  /// 编辑配置
  Future<void> editConfig(AIConfigModel config) async {
    logger.i('[AIConfigController] 编辑配置: ${config.name}');
    final result = await Get.toNamed(
      Routes.aiConfigEdit,
      arguments: {'aiConfig': config},
    );
    _handleEditResult(result);
  }

  /// 删除配置
  Future<bool> deleteConfig(AIConfigModel config) async {
    logger.i('[AIConfigController] 删除配置: ${config.name}');
    return await _aiConfigStateService.deleteConfig(config);
  }

  /// 设为默认配置
  Future<bool> setAsDefault(AIConfigModel config) async {
    logger.i('[AIConfigController] 设置默认配置: ${config.name}');
    return await _aiConfigStateService.setAsDefault(config);
  }

  /// 克隆配置
  Future<void> cloneConfig(AIConfigModel config) async {
    logger.i('[AIConfigController] 克隆配置: ${config.name}');
    await _aiConfigStateService.cloneConfig(config);
  }

  // ========================================================================
  // 私有方法
  // ========================================================================

  /// 处理编辑返回结果
  Future<void> _handleEditResult(dynamic result) async {
    if (result is! Map<String, dynamic>) return;

    final action = result['action'];
    final config = result['config'] as AIConfigModel?;

    if (config == null) return;

    if (action == 'created') {
      _aiConfigStateService.addConfigToList(config);
      logger.i('[AIConfigController] 添加新配置: ${config.name}');
    } else if (action == 'updated') {
      _aiConfigStateService.updateConfigInList(config);
      logger.i('[AIConfigController] 更新配置: ${config.name}');
    }

    // 确保UI更新
    configs.refresh();
  }

  // ========================================================================
  // 辅助方法
  // ========================================================================

  /// 获取配置类型图标
  IconData getTypeIcon(int type) => AIConfigTypes.getIcon(type);

  /// 获取配置类型颜色
  Color getTypeColor(int type) => AIConfigTypes.getColor(type);

  /// 获取配置类型名称
  String getTypeName(int type) => AIConfigTypes.getName(type);
}
