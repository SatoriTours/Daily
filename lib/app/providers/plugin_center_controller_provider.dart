/// Plugin Center Controller Provider
///
/// 插件中心控制器，管理插件中心页面的UI状态和用户交互。

library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/utils/utils.dart';

part 'plugin_center_controller_provider.freezed.dart';
part 'plugin_center_controller_provider.g.dart';

/// PluginCenterController 状态
@freezed
abstract class PluginCenterControllerState with _$PluginCenterControllerState {
  const factory PluginCenterControllerState({
    @Default(false) bool isLoading,
    @Default('') String updatingPluginId,
    @Default('') String pluginServerUrl,
  }) = _PluginCenterControllerState;
}

/// PluginCenterController Provider
@riverpod
class PluginCenterController extends _$PluginCenterController {
  @override
  PluginCenterControllerState build() {
    _loadPluginData();
    return const PluginCenterControllerState();
  }

  void _loadPluginData() {
    final url = PluginService.i.getPluginServerUrl();
    state = state.copyWith(pluginServerUrl: url);
  }

  Future<void> updateServerUrl(String url) async {
    await PluginService.i.setPluginServerUrl(url);
    state = state.copyWith(pluginServerUrl: url);
  }

  Future<void> updatePlugin(PluginInfo plugin) async {
    state = state.copyWith(updatingPluginId: plugin.fileName);
    try {
      final success = await PluginService.i.forceUpdatePlugin(plugin.fileName);
      if (success) {
        UIUtils.showSuccess('插件更新成功');
      } else {
        UIUtils.showError('插件更新失败');
      }
    } catch (e) {
      UIUtils.showError('插件更新失败: $e');
    } finally {
      state = state.copyWith(updatingPluginId: '');
    }
  }
  
  Future<void> updateAllPlugins() async {
    state = state.copyWith(isLoading: true);
    try {
      final plugins = PluginService.i.getAllPlugins();
      for (final plugin in plugins) {
        await PluginService.i.forceUpdatePlugin(plugin.fileName);
      }
      UIUtils.showSuccess('所有插件更新完成');
    } catch (e) {
      UIUtils.showError('更新失败: $e');
    } finally {
      state = state.copyWith(isLoading: false);
    }
  }
}
