/// 首次设置引导组件
///
/// 用于首次启动时显示必要配置的引导信息

library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/first_launch_provider.dart';
import 'package:daily_satori/app/styles/styles.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';

/// 首次设置引导卡片
class FirstSetupGuide extends ConsumerWidget {
  const FirstSetupGuide({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final setupState = ref.watch(firstLaunchControllerProvider);
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingM),
      padding: const EdgeInsets.all(Dimensions.spacingL),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [colorScheme.primary.withValues(alpha: 0.1), colorScheme.secondary.withValues(alpha: 0.1)],
        ),
        borderRadius: BorderRadius.circular(Dimensions.radiusL),
        border: Border.all(color: colorScheme.primary.withValues(alpha: 0.3), width: 2),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 标题
          _buildHeader(context, textTheme, colorScheme, setupState),
          Dimensions.verticalSpacerL,
          // 说明文字
          _buildDescription(context, textTheme),
          Dimensions.verticalSpacerL,
          // 配置项列表
          _buildSetupItems(context, ref, textTheme, colorScheme, setupState),
          Dimensions.verticalSpacerL,
          // 进度提示
          _buildProgressIndicator(context, textTheme, colorScheme, setupState),
        ],
      ),
    );
  }

  /// 构建标题
  Widget _buildHeader(BuildContext context, TextTheme textTheme, ColorScheme colorScheme, FirstLaunchState setupState) {
    final isAllComplete = setupState.isSetupComplete;
    final pendingCount = setupState.pendingCount;

    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(Dimensions.spacingS),
          decoration: BoxDecoration(
            color: isAllComplete
                ? AppColors.getSuccess(context).withValues(alpha: 0.2)
                : colorScheme.primary.withValues(alpha: 0.2),
            shape: BoxShape.circle,
          ),
          child: Icon(
            isAllComplete ? Icons.check_circle_rounded : Icons.info_rounded,
            color: isAllComplete ? AppColors.getSuccess(context) : colorScheme.primary,
            size: Dimensions.iconSizeL,
          ),
        ),
        Dimensions.horizontalSpacerM,
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                isAllComplete ? '设置已完成' : '欢迎使用 Daily Satori',
                style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold, color: colorScheme.primary),
              ),
              if (!isAllComplete)
                Text(
                  '还有 $pendingCount 项必要配置需要完成',
                  style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.7)),
                ),
            ],
          ),
        ),
      ],
    );
  }

  /// 构建说明文字
  Widget _buildDescription(BuildContext context, TextTheme textTheme) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      padding: const EdgeInsets.all(Dimensions.spacingM),
      decoration: BoxDecoration(
        color: colorScheme.primary.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
      ),
      child: Text(
        '为了让应用正常工作，请先完成 AI 配置（必填）。其他两项为可选配置，可根据需要设置。',
        style: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.8), height: 1.5),
      ),
    );
  }

  /// 构建配置项列表
  Widget _buildSetupItems(
    BuildContext context,
    WidgetRef ref,
    TextTheme textTheme,
    ColorScheme colorScheme,
    FirstLaunchState setupState,
  ) {
    return Column(
      children: [
        _buildSetupItem(
          context: context,
          ref: ref,
          title: 'AI 配置',
          subtitle: '配置 AI API Key 以启用文章分析、日记总结、书籍解读等核心功能（必填）',
          icon: Icons.smart_toy_rounded,
          isComplete: setupState.hasAIConfig,
          color: AppColors.getPrimary(context),
          isRequired: true,
          onTap: () => AppNavigation.toNamed(Routes.aiConfig),
        ),
        Dimensions.verticalSpacerM,
        _buildSetupItem(
          context: context,
          ref: ref,
          title: 'Google Cloud API Key',
          subtitle: '配置 Google Books API Key 以获取书籍详细信息、封面和摘要（可选）',
          icon: Icons.menu_book_rounded,
          isComplete: setupState.hasGoogleCloudKey,
          color: AppColors.getInfo(context),
          isRequired: false,
          onTap: () => _showGoogleCloudKeyDialog(context, ref),
        ),
        Dimensions.verticalSpacerM,
        // 备份目录选择
        if (!setupState.hasBackupDir)
          _buildSetupItem(
            context: context,
            ref: ref,
            title: '备份目录',
            subtitle: '设置备份目录以确保数据安全，支持应用数据的备份和恢复（可选）',
            icon: Icons.folder_rounded,
            isComplete: false,
            color: AppColors.getSuccess(context),
            isRequired: false,
            onTap: () async {
              await AppNavigation.toNamed(Routes.backupSettings);
              // 返回后刷新状态
              ref.invalidate(firstLaunchControllerProvider);
            },
          ),
        // 已配置备份目录后显示恢复选项
        if (setupState.hasBackupDir) ...[
          _buildSetupItem(
            context: context,
            ref: ref,
            title: '备份目录',
            subtitle: '已设置备份目录',
            icon: Icons.folder_rounded,
            isComplete: true,
            color: AppColors.getSuccess(context),
            isRequired: false,
            canClickWhenComplete: true,
            onTap: () async {
              await AppNavigation.toNamed(Routes.backupSettings);
              // 返回后刷新状态
              ref.invalidate(firstLaunchControllerProvider);
            },
          ),
          Dimensions.verticalSpacerM,
          _buildSetupItem(
            context: context,
            ref: ref,
            title: '从备份恢复',
            subtitle: '从已有备份中恢复应用数据',
            icon: Icons.restore_rounded,
            isComplete: false,
            color: AppColors.getInfo(context),
            isRequired: false,
            canClickWhenComplete: true,
            onTap: () => AppNavigation.toNamed(Routes.backupRestore),
          ),
        ],
      ],
    );
  }

  /// 构建单个配置项
  Widget _buildSetupItem({
    required BuildContext context,
    required WidgetRef ref,
    required String title,
    required String subtitle,
    required IconData icon,
    required bool isComplete,
    required Color color,
    required bool isRequired,
    required VoidCallback onTap,
    bool canClickWhenComplete = false,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return InkWell(
      onTap: (isComplete && !canClickWhenComplete) ? null : onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: Container(
        padding: const EdgeInsets.all(Dimensions.spacingM),
        decoration: BoxDecoration(
          color: isComplete
              ? AppColors.getSuccess(context).withValues(alpha: 0.1)
              : colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          border: Border.all(
            color: isComplete
                ? AppColors.getSuccess(context).withValues(alpha: 0.5)
                : colorScheme.outline.withValues(alpha: 0.2),
            width: isComplete ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            // 图标
            Container(
              width: Dimensions.iconSizeXl + Dimensions.spacingS,
              height: Dimensions.iconSizeXl + Dimensions.spacingS,
              decoration: BoxDecoration(
                color: isComplete
                    ? AppColors.getSuccess(context).withValues(alpha: 0.2)
                    : color.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
              ),
              child: Icon(
                isComplete ? Icons.check_circle_rounded : icon,
                color: isComplete ? AppColors.getSuccess(context) : color,
                size: Dimensions.iconSizeM,
              ),
            ),
            Dimensions.horizontalSpacerM,
            // 文字
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        title,
                        style: textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w600,
                          color: isComplete ? AppColors.getSuccess(context) : colorScheme.onSurface,
                        ),
                      ),
                      if (isRequired) ...[
                        Dimensions.horizontalSpacerXs,
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingXs, vertical: 2),
                          decoration: BoxDecoration(
                            color: AppColors.getError(context).withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(Dimensions.radiusXs),
                            border: Border.all(color: AppColors.getError(context).withValues(alpha: 0.5), width: 1),
                          ),
                          child: Text(
                            '必填',
                            style: textTheme.labelSmall?.copyWith(
                              color: AppColors.getError(context),
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  Dimensions.verticalSpacerXs,
                  Text(
                    subtitle,
                    style: textTheme.bodySmall?.copyWith(
                      color: colorScheme.onSurface.withValues(alpha: 0.7),
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            // 状态图标
            if (isComplete)
              Icon(Icons.check_circle_rounded, color: AppColors.getSuccess(context), size: Dimensions.iconSizeL)
            else
              Icon(
                Icons.chevron_right_rounded,
                color: colorScheme.onSurface.withValues(alpha: 0.4),
                size: Dimensions.iconSizeM,
              ),
          ],
        ),
      ),
    );
  }

  /// 构建进度指示器
  Widget _buildProgressIndicator(
    BuildContext context,
    TextTheme textTheme,
    ColorScheme colorScheme,
    FirstLaunchState setupState,
  ) {
    final completedCount = 3 - setupState.pendingCount;
    final progress = completedCount / 3;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              '配置进度',
              style: textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.primary),
            ),
            Text(
              '$completedCount/3',
              style: textTheme.labelLarge?.copyWith(fontWeight: FontWeight.bold, color: colorScheme.primary),
            ),
          ],
        ),
        Dimensions.verticalSpacerS,
        ClipRRect(
          borderRadius: BorderRadius.circular(Dimensions.radiusS),
          child: LinearProgressIndicator(
            value: progress,
            minHeight: 8,
            backgroundColor: colorScheme.surfaceContainerHighest,
            valueColor: AlwaysStoppedAnimation<Color>(
              setupState.isSetupComplete ? AppColors.getSuccess(context) : colorScheme.primary,
            ),
          ),
        ),
      ],
    );
  }

  /// 显示 Google Cloud API Key 配置对话框
  void _showGoogleCloudKeyDialog(BuildContext context, WidgetRef ref) {
    DialogUtils.showInputDialog(
      title: '配置 Google Cloud API Key',
      hintText: '输入您的 Google Cloud API Key',
      initialValue: SettingRepository.i.getSetting(SettingService.googleCloudApiKeyKey),
      confirmText: '保存',
      cancelText: '取消',
      onConfirm: (value) {
        final apiKey = value.trim();
        SettingRepository.i.saveSetting(SettingService.googleCloudApiKeyKey, apiKey);
        UIUtils.showSuccess('保存成功');
        // 保存后刷新状态
        ref.invalidate(firstLaunchControllerProvider);
      },
    );
  }
}
