import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';
import 'package:daily_satori/app/modules/plugin_center/views/widgets/server_url_dialog.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 插件中心视图
class PluginCenterView extends GetView<PluginCenterController> {
  const PluginCenterView({super.key});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final isLoading = controller.isLoading.value;
      final plugins = controller.plugins;
      final hasPlugins = plugins.isNotEmpty;
      final isUpdatingAny = controller.updatingPlugin.isNotEmpty;

      return Scaffold(
        backgroundColor: AppColors.getSurface(context),
        appBar: _buildAppBar(context, hasPlugins, isUpdatingAny),
        body: _buildBody(context, isLoading, plugins),
      );
    });
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context, bool hasPlugins, bool isUpdatingAny) {
    return AppBar(
      title: const Text('插件中心'),
      centerTitle: true,
      elevation: 0,
      backgroundColor: AppColors.getSurface(context),
      actions: [
        // 更新所有按钮
        IconButton(
          icon: Icon(
            Icons.refresh_rounded,
            color: (hasPlugins && !isUpdatingAny)
                ? AppColors.getPrimary(context)
                : AppColors.getOnSurface(context).withValues(alpha: Opacities.low),
          ),
          tooltip: '更新所有插件',
          onPressed: (hasPlugins && !isUpdatingAny) ? () => _updateAllPlugins(context) : null,
        ),
        // 服务器设置按钮
        IconButton(
          icon: const Icon(Icons.settings_rounded),
          tooltip: '服务器设置',
          onPressed: () => ServerUrlDialog.show(context, controller),
        ),
      ],
    );
  }

  /// 构建主体内容
  Widget _buildBody(BuildContext context, bool isLoading, List plugins) {
    if (isLoading) {
      return StyleGuide.getLoadingState(context);
    }

    if (plugins.isEmpty) {
      return _buildEmptyView(context);
    }

    return RefreshIndicator(
      onRefresh: controller.loadPluginData,
      color: AppColors.getPrimary(context),
      child: ListView.separated(
        itemCount: plugins.length,
        padding: Dimensions.paddingPage,
        physics: const AlwaysScrollableScrollPhysics(parent: BouncingScrollPhysics()),
        separatorBuilder: (context, index) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          final plugin = plugins[index];
          final isUpdating = controller.updatingPlugin.value == plugin.fileName;
          return _buildPluginCard(context, plugin, isUpdating);
        },
      ),
    );
  }

  /// 构建空视图
  Widget _buildEmptyView(BuildContext context) {
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
              width: 120,
              height: 120,
              decoration: BoxDecoration(color: colorScheme.primary.withValues(alpha: 0.1), shape: BoxShape.circle),
              child: Icon(Icons.extension_off_rounded, size: 60, color: colorScheme.primary.withValues(alpha: 0.5)),
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
                  onPressed: controller.loadPluginData,
                  icon: const Icon(Icons.refresh_rounded, size: 18),
                  label: const Text('刷新'),
                  style: ButtonStyles.getPrimaryStyle(context),
                ),
                Dimensions.horizontalSpacerM,

                // 设置按钮
                OutlinedButton.icon(
                  onPressed: () => ServerUrlDialog.show(context, controller),
                  icon: const Icon(Icons.settings_rounded, size: 18),
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

  /// 构建插件卡片
  Widget _buildPluginCard(BuildContext context, PluginInfo plugin, bool isUpdating) {
    final color = _getPluginColor(plugin);

    return Card(
      margin: EdgeInsets.zero,
      child: InkWell(
        onTap: isUpdating ? null : () => _showPluginDetails(context, plugin),
        child: Padding(
          padding: Dimensions.paddingCard,
          child: Row(
            children: [
              // 插件图标 - 使用更清晰的文档图标
              FeatureIcon(
                icon: Icons.description_rounded,
                iconColor: color,
                containerSize: Dimensions.iconSizeL,
                iconSize: Dimensions.iconSizeS,
              ),
              Dimensions.horizontalSpacerM,

              // 插件信息
              Expanded(child: _buildPluginInfo(context, plugin, isUpdating)),

              // 右侧更新状态（移除箭头）
              if (isUpdating) _buildUpdatingIndicator(context),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建插件信息
  Widget _buildPluginInfo(BuildContext context, PluginInfo plugin, bool isUpdating) {
    final timeText = controller.getUpdateTimeText(plugin.lastUpdateTime);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 第一行：插件名称和更新时间
        Row(
          children: [
            // 插件名称
            Expanded(
              child: Text(
                plugin.fileName,
                style: AppTypography.titleSmall,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 8),
            // 更新时间 - 右对齐
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  Icons.schedule_rounded,
                  size: 14,
                  color: AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
                ),
                const SizedBox(width: 4),
                Text(
                  timeText,
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.getOnSurface(context).withValues(alpha: Opacities.medium),
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 4),

        // 描述 - 使用 high 透明度提升可读性
        if (plugin.description.isNotEmpty)
          Text(
            plugin.description,
            style: AppTypography.bodySmall.copyWith(
              color: AppColors.getOnSurface(context).withValues(alpha: Opacities.high), // 改为 high 提升可读性
              height: 1.3,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
      ],
    );
  }

  /// 获取插件颜色
  Color _getPluginColor(PluginInfo plugin) {
    // 根据插件名称的首字母分配颜色
    final firstChar = plugin.fileName.isNotEmpty ? plugin.fileName[0].toLowerCase() : 'a';
    final charCode = firstChar.codeUnitAt(0);
    final colorIndex = charCode % 6;

    switch (colorIndex) {
      case 0:
        return Colors.blue;
      case 1:
        return Colors.green;
      case 2:
        return Colors.orange;
      case 3:
        return Colors.purple;
      case 4:
        return Colors.teal;
      case 5:
        return Colors.pink;
      default:
        return Colors.blue;
    }
  }

  /// 构建更新中指示器
  Widget _buildUpdatingIndicator(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow),
        borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 12,
            height: 12,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
            ),
          ),
          const SizedBox(width: 6),
          Text('更新中', style: AppTypography.labelSmall.copyWith(color: AppColors.getPrimary(context))),
        ],
      ),
    );
  }

  /// 显示插件详情
  void _showPluginDetails(BuildContext context, PluginInfo plugin) {
    final color = _getPluginColor(plugin);

    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.getSurface(context),
      shape: const RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusTop),
      builder: (context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 标题栏
            Row(
              children: [
                FeatureIcon(
                  icon: Icons.description_rounded,
                  iconColor: color,
                  containerSize: Dimensions.iconSizeL,
                  iconSize: Dimensions.iconSizeS,
                ),
                Dimensions.horizontalSpacerM,
                Expanded(
                  child: Text(plugin.fileName, style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.w600)),
                ),
                IconButton(icon: const Icon(Icons.close_rounded), onPressed: () => Navigator.pop(context)),
              ],
            ),
            Dimensions.verticalSpacerL,

            // 描述
            if (plugin.description.isNotEmpty) ...[
              Text(
                '插件描述',
                style: AppTypography.labelLarge.copyWith(
                  color: AppColors.getPrimary(context),
                  fontWeight: FontWeight.w600,
                ),
              ),
              Dimensions.verticalSpacerS,
              Text(
                plugin.description,
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.getOnSurface(context).withValues(alpha: Opacities.high), // 改为 high 提升可读性
                  height: 1.5,
                ),
              ),
              Dimensions.verticalSpacerM,
            ],

            // 更新时间信息卡片
            Container(
              padding: Dimensions.paddingCard,
              decoration: BoxDecoration(
                color: AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusM),
                border: Border.all(color: AppColors.getPrimary(context).withValues(alpha: Opacities.extraLow * 2)),
              ),
              child: Row(
                children: [
                  Icon(Icons.update_rounded, size: Dimensions.iconSizeS, color: AppColors.getPrimary(context)),
                  Dimensions.horizontalSpacerS,
                  Text('上次更新：', style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.w500)),
                  Expanded(
                    child: Text(
                      controller.getUpdateTimeText(plugin.lastUpdateTime),
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.getOnSurface(context).withValues(alpha: Opacities.mediumHigh),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            Dimensions.verticalSpacerL,

            // 更新按钮
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: () {
                  Navigator.pop(context);
                  _updatePlugin(plugin);
                },
                icon: const Icon(Icons.refresh_rounded),
                label: const Text('更新插件'),
                style: ButtonStyles.getPrimaryStyle(context),
              ),
            ),
            Dimensions.verticalSpacerS,
          ],
        ),
      ),
    );
  }

  /// 更新插件
  void _updatePlugin(PluginInfo plugin) {
    // 如果服务器URL未设置，提示设置
    if (controller.pluginServerUrl.value.isEmpty) {
      UIUtils.showError('请先设置插件服务器地址');
      return;
    }

    // 执行更新
    controller
        .updatePlugin(plugin.fileName)
        .then((result) {
          if (result) {
            UIUtils.showSuccess('插件更新成功');
          } else {
            UIUtils.showError('插件更新失败');
          }
        })
        .catchError((e) {
          UIUtils.showError('更新出错: $e');
        });
  }

  /// 更新所有插件
  Future<void> _updateAllPlugins(BuildContext context) async {
    // 如果服务器URL未设置，提示设置
    if (controller.pluginServerUrl.value.isEmpty) {
      UIUtils.showError('请先设置插件服务器地址');
      ServerUrlDialog.show(context, controller);
      return;
    }

    try {
      final result = await controller.updateAllPlugins();

      if (result) {
        UIUtils.showSuccess('所有插件已成功更新');
      } else {
        UIUtils.showError('部分插件更新失败');
      }
    } catch (e) {
      UIUtils.showError('更新出错: $e');
    }
  }
}
