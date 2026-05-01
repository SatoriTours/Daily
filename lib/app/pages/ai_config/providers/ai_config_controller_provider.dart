/// AI Config Controller Provider
///
/// AI配置控制器，管理AI配置页面的UI状态和用户交互。

library;

import 'package:freezed_annotation/freezed_annotation.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'ai_config_controller_provider.freezed.dart';
part 'ai_config_controller_provider.g.dart';

/// AIConfigController 状态
@freezed
abstract class AIConfigControllerState with _$AIConfigControllerState {
  const factory AIConfigControllerState({
    /// 选中的功能类型
    @Default(0) int selectedFunctionType,

    /// 是否正在加载
    @Default(false) bool isLoading,

    /// 配置列表
    @Default([]) List<AIConfigModel> configs,
  }) = _AIConfigControllerState;
}

/// AIConfigController Provider
@riverpod
class AIConfigController extends _$AIConfigController {
  @override
  AIConfigControllerState build() {
    return AIConfigControllerState(configs: AIConfigRepository.i.allModels());
  }

  /// 刷新配置列表
  void refreshConfigs() {
    state = state.copyWith(configs: AIConfigRepository.i.allModels());
  }

  /// 切换功能类型
  void changeFunctionType(int type) {
    state = state.copyWith(selectedFunctionType: type);
  }

  /// 创建新配置
  Future<void> createNewConfig() async {
    logger.i('[AIConfigController] 创建新配置');
    await AppNavigation.toNamed(
      Routes.aiConfigEdit,
      arguments: {'functionType': state.selectedFunctionType},
    );
    refreshConfigs();
  }

  /// 编辑配置
  Future<void> editConfig(AIConfigModel config) async {
    logger.i('[AIConfigController] 编辑配置: ${config.name}');
    await AppNavigation.toNamed(
      Routes.aiConfigEdit,
      arguments: {'aiConfig': config.entity},
    );
    refreshConfigs();
  }

  /// 删除配置
  Future<void> deleteConfig(AIConfigModel config) async {
    logger.i('[AIConfigController] 删除配置: ${config.name}');
    AIConfigRepository.i.remove(config.id);
    refreshConfigs();
  }
}
