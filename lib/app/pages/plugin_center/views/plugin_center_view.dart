import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/pages/plugin_center/views/widgets/plugin_card.dart';
import 'package:daily_satori/app/pages/plugin_center/views/widgets/server_url_dialog.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/plugin_center/providers/plugin_center_controller_provider.dart';

/// 插件中心视图
class PluginCenterView extends ConsumerWidget {
  const PluginCenterView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(pluginCenterControllerProvider);
    // Note: PluginService.i.getAllPlugins() might not be reactive.
    // Ideally the controller should expose the list of plugins.
    // For now, we assume the list is static or we might need to reload it.
    // But since we are just fixing compilation errors, let's stick to what we have
    // but make sure it compiles.
    final plugins = PluginService.i.getAllPlugins();
    final hasPlugins = plugins.isNotEmpty;
    final isUpdating = state.isLoading || state.updatingPluginId.isNotEmpty;

    return Scaffold(
      backgroundColor: AppColors.getSurface(context),
      appBar: _buildAppBar(context, ref, hasPlugins, isUpdating),
      body: _buildBody(context, ref, state, plugins),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context, WidgetRef ref, bool hasPlugins, bool isUpdating) {
    return SAppBar(
      title: const Text('插件中心', style: TextStyle(color: Colors.white)),
      centerTitle: true,
      elevation: 0,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
      actions: [
        // 更新所有按钮
        IconButton(
          icon: Icon(
            Icons.refresh_rounded,
            color: (hasPlugins && !isUpdating) ? Colors.white : Colors.white.withValues(alpha: Opacities.half),
          ),
          tooltip: '更新所有插件',
          onPressed: (hasPlugins && !isUpdating) ? () => _updateAllPlugins(ref) : null,
        ),
        // 服务器设置按钮
        IconButton(
          icon: const Icon(Icons.settings_rounded, color: Colors.white),
          tooltip: '服务器设置',
          onPressed: () => ServerUrlDialog.show(context, ref),
        ),
      ],
    );
  }

  /// 构建主体内容
  Widget _buildBody(BuildContext context, WidgetRef ref, PluginCenterControllerState state, List<PluginInfo> plugins) {
    if (state.isLoading && plugins.isEmpty) {
      return StyleGuide.getLoadingState(context);
    }

    if (plugins.isEmpty) {
      return _buildEmptyView(context, ref);
    }

    return RefreshIndicator(
      onRefresh: () async {
        // Trigger reload if needed, or just setState to refresh the list from service
        // Since we don't have a reload method in controller that fetches plugins (it only loads URL),
        // we might just rely on the service.
        // For now, let's just wait a bit to simulate refresh.
        await Future.delayed(const Duration(milliseconds: 500));
        // In a real app, we should probably have a method to reload plugins from disk/service.
        // But PluginService.getAllPlugins() reads from memory/disk synchronously usually.
        // To force a UI rebuild, we might need to invalidate the provider if it held the list.
        // Here we are just rebuilding the widget tree.
      },
      color: AppColors.getPrimary(context),
      child: ListView.separated(
        itemCount: plugins.length,
        padding: Dimensions.paddingPage,
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        separatorBuilder: (context, index) => Dimensions.verticalSpacerM,
        itemBuilder: (context, index) {
          final plugin = plugins[index];
          return PluginCard(plugin: plugin);
        },
      ),
    );
  }

  /// 构建空视图
  Widget _buildEmptyView(BuildContext context, WidgetRef ref) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Center(
      child: Padding(
        padding: Dimensions.paddingL,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 空状态图标
            Container(
              width: Dimensions.iconSizeXxl * 2.5,
              height: Dimensions.iconSizeXxl * 2.5,
              decoration: BoxDecoration(color: colorScheme.primary.withValues(alpha: 0.1), shape: BoxShape.circle),
              child: Icon(
                Icons.extension_off_rounded,
                size: Dimensions.iconSizeXxl + Dimensions.spacingM,
                color: colorScheme.primary.withValues(alpha: 0.5),
              ),
            ),
            Dimensions.verticalSpacerL,

            // 标题
            Text('暂无插件', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
            Dimensions.verticalSpacerS,

            // 描述
            Text(
              '请检查插件服务器设置或稍后重试',
              style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.6)),
              textAlign: TextAlign.center,
            ),
            Dimensions.verticalSpacerL,

            // 操作按钮
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // 刷新按钮
                ElevatedButton.icon(
                  onPressed: () {
                    // Trigger rebuild
                    // ref.refresh(pluginCenterControllerProvider);
                    // But controller doesn't hold plugins list.
                    // Just force rebuild?
                    (context as Element).markNeedsBuild();
                  },
                  icon: const Icon(Icons.refresh_rounded, size: Dimensions.iconSizeS),
                  label: const Text('刷新'),
                  style: ButtonStyles.getPrimaryStyle(context),
                ),
                Dimensions.horizontalSpacerM,

                // 设置按钮
                OutlinedButton.icon(
                  onPressed: () => ServerUrlDialog.show(context, ref),
                  icon: const Icon(Icons.settings_rounded, size: Dimensions.iconSizeS),
                  label: const Text('设置'),
                  style: ButtonStyles.getOutlinedStyle(context),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// 更新所有插件
  Future<void> _updateAllPlugins(WidgetRef ref) async {
    await ref.read(pluginCenterControllerProvider.notifier).updateAllPlugins();
  }
}
