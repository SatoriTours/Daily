import 'package:daily_satori/app/pages/plugin_center/providers/plugin_center_controller_provider.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 插件卡片组件
class PluginCard extends ConsumerWidget {
  /// 插件信息
  final PluginInfo plugin;

  /// 构造函数
  const PluginCard({super.key, required this.plugin});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(pluginCenterControllerProvider);
    final isUpdating = state.updatingPluginId == plugin.fileName;

    return Card(
      margin: const EdgeInsets.only(bottom: Dimensions.spacingM),
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusM),
      child: Padding(
        padding: Dimensions.paddingM,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 标题行
            _buildTitleRow(context, ref, isUpdating, state),
            Dimensions.verticalSpacerM,

            // 描述
            _buildDescription(context),
            Dimensions.verticalSpacerM,

            // 更新时间
            _buildUpdateTime(context),
          ],
        ),
      ),
    );
  }

  /// 构建标题行
  Widget _buildTitleRow(
    BuildContext context,
    WidgetRef ref,
    bool isUpdating,
    PluginCenterControllerState state,
  ) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // 图标
        Container(
          decoration: BoxDecoration(
            color: AppColors.getPrimary(
              context,
            ).withValues(alpha: Opacities.low),
            borderRadius: Dimensions.borderRadiusS,
          ),
          padding: Dimensions.paddingS,
          child: Icon(
            Icons.settings_input_svideo,
            color: AppColors.getPrimary(context),
            size: Dimensions.iconSizeM,
          ),
        ),
        Dimensions.horizontalSpacerM,

        // 文件名
        Expanded(
          child: Text(
            plugin.fileName,
            style: AppTypography.titleMedium.copyWith(
              fontWeight: FontWeight.bold,
            ),
          ),
        ),

        // 更新按钮或进度指示器
        if (isUpdating)
          _buildUpdatingIndicator(context)
        else
          _buildUpdateButton(context, ref, state),
      ],
    );
  }

  /// 构建更新中指示器
  Widget _buildUpdatingIndicator(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: Dimensions.spacingM,
        vertical: Dimensions.spacingXs + 2,
      ),
      decoration: BoxDecoration(
        color: AppColors.getPrimary(context).withValues(alpha: Opacities.low),
        borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: Dimensions.iconSizeXs - 2,
            height: Dimensions.iconSizeXs - 2,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(
                AppColors.getPrimary(context),
              ),
            ),
          ),
          Dimensions.horizontalSpacerS,
          Text(
            '更新中',
            style: AppTypography.labelSmall.copyWith(
              color: AppColors.getPrimary(context),
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  /// 构建更新按钮
  Widget _buildUpdateButton(
    BuildContext context,
    WidgetRef ref,
    PluginCenterControllerState state,
  ) {
    final isEnabled = state.updatingPluginId.isEmpty;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
      child: InkWell(
        borderRadius: BorderRadius.circular(Dimensions.radiusCircular),
        onTap: isEnabled ? () => _updatePlugin(ref, state) : null,
        child: Padding(
          padding: Dimensions.paddingS,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.refresh,
                color: isEnabled
                    ? AppColors.getPrimary(context)
                    : AppColors.getOnSurface(
                        context,
                      ).withValues(alpha: Opacities.medium),
                size: Dimensions.iconSizeM + 2,
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建描述文本
  Widget _buildDescription(BuildContext context) {
    return Text(
      plugin.description,
      style: AppTypography.bodyMedium.copyWith(
        color: AppColors.getOnSurface(
          context,
        ).withValues(alpha: Opacities.mediumHigh),
        height: 1.4,
      ),
    );
  }

  /// 构建更新时间信息
  Widget _buildUpdateTime(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: Dimensions.spacingS + 2,
        vertical: Dimensions.spacingXs + 2,
      ),
      decoration: BoxDecoration(
        color: AppColors.getOnSurface(
          context,
        ).withValues(alpha: Opacities.extraLow),
        borderRadius: Dimensions.borderRadiusS,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.update,
            size: Dimensions.iconSizeXs - 2,
            color: AppColors.getOnSurface(
              context,
            ).withValues(alpha: Opacities.medium),
          ),
          Dimensions.horizontalSpacerXs,
          Text(
            '上次更新: ${_getUpdateTimeText(plugin.lastUpdateTime)}',
            style: AppTypography.labelSmall.copyWith(
              color: AppColors.getOnSurface(
                context,
              ).withValues(alpha: Opacities.medium),
            ),
          ),
        ],
      ),
    );
  }

  String _getUpdateTimeText(DateTime? time) {
    if (time == null) return '从未更新';
    return '${time.year}-${time.month.toString().padLeft(2, '0')}-${time.day.toString().padLeft(2, '0')} ${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
  }

  /// 更新插件
  void _updatePlugin(WidgetRef ref, PluginCenterControllerState state) {
    // 如果服务器URL未设置，提示设置
    if (state.pluginServerUrl.isEmpty) {
      UIUtils.showSnackBar('未设置服务器', '请先设置插件服务器地址', isError: true);
      return;
    }

    // 执行更新
    ref.read(pluginCenterControllerProvider.notifier).updatePlugin(plugin);
  }
}
