import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';

import '../controllers/backup_settings_controller.dart';

class BackupSettingsView extends GetView<BackupSettingsController> {
  const BackupSettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: AppBar(
        title: Text('备份与恢复', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
        centerTitle: true,
        elevation: 0,
        backgroundColor: colorScheme.surface,
      ),
      body: Obx(() {
        // 收集所有响应式状态
        final hasBackupDirectory = controller.backupDirectory.value.isNotEmpty;
        final isBackingUp = controller.isBackingUp.value;
        final backupProgress = controller.backupProgress.value;

        // 如果还没有选择备份目录，直接显示提示选择
        if (!hasBackupDirectory) {
          return _buildSelectDirectoryPrompt(context);
        }

        // 已有备份目录，显示正常界面
        return _buildMainContent(context, isBackingUp, backupProgress);
      }),
    );
  }

  /// 构建选择目录提示
  Widget _buildSelectDirectoryPrompt(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Center(
      child: Padding(
        padding: Dimensions.paddingPage,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 图标容器
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                color: colorScheme.primaryContainer.withValues(alpha: Opacities.high),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.folder_open_rounded, size: 64, color: colorScheme.primary),
            ),
            Dimensions.verticalSpacerL,
            Dimensions.verticalSpacerL,
            Text('请选择备份目录', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
            Dimensions.verticalSpacerM,
            Text(
              '选择一个文件夹存储您的应用数据备份',
              style: textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurface.withValues(alpha: Opacities.higherOpaque),
              ),
              textAlign: TextAlign.center,
            ),
            Dimensions.verticalSpacerL,
            Dimensions.verticalSpacerL,
            ElevatedButton.icon(
              onPressed: () => controller.selectBackupDirectory(),
              icon: Icon(Icons.create_new_folder_rounded, size: Dimensions.iconSizeM),
              label: const Text('选择备份目录'),
              style: ButtonStyles.getPrimaryStyle(context).copyWith(
                padding: WidgetStatePropertyAll(
                  EdgeInsets.symmetric(horizontal: Dimensions.spacingXl, vertical: Dimensions.spacingM),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 构建主要内容
  Widget _buildMainContent(BuildContext context, bool isBackingUp, double backupProgress) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: Dimensions.paddingPage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 备份目录区域
          _buildSectionTitle(context, '备份目录', Icons.folder_rounded),
          Dimensions.verticalSpacerM,
          _buildDirectoryCard(context),
          Dimensions.verticalSpacerL,
          Dimensions.verticalSpacerL,

          // 操作按钮区域
          _buildSectionTitle(context, '操作', Icons.touch_app_rounded),
          Dimensions.verticalSpacerM,

          // 立即备份按钮
          isBackingUp
              ? _buildBackupProgress(context, backupProgress)
              : _buildActionButton(
                  context,
                  title: '立即备份',
                  subtitle: '保存当前所有应用数据',
                  icon: Icons.backup_rounded,
                  color: colorScheme.primary,
                  onTap: () => _onBackupPressed(context),
                ),
          Dimensions.verticalSpacerM,

          // 恢复备份按钮
          _buildActionButton(
            context,
            title: '恢复备份',
            subtitle: '从备份文件中恢复数据',
            icon: Icons.restore_rounded,
            color: Colors.deepPurple,
            onTap: () => Get.toNamed(Routes.backupRestore),
          ),
          Dimensions.verticalSpacerL,
          Dimensions.verticalSpacerL,

          // 提示信息
          Container(
            padding: Dimensions.paddingM,
            decoration: BoxDecoration(
              color: colorScheme.primary.withValues(alpha: Opacities.low),
              borderRadius: BorderRadius.circular(Dimensions.radiusM),
              border: Border.all(color: colorScheme.primary.withValues(alpha: Opacities.medium), width: 1),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.lightbulb_outline_rounded, size: Dimensions.iconSizeM, color: colorScheme.primary),
                    Dimensions.horizontalSpacerS,
                    Text(
                      '备份说明',
                      style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.primary),
                    ),
                  ],
                ),
                Dimensions.verticalSpacerS,
                Text(
                  '• 备份将保存您的全部应用数据，包括文章、笔记和设置\n'
                  '• 备份文件存储在您选择的目录中\n'
                  '• 建议定期备份数据以防丢失',
                  style: textTheme.bodySmall?.copyWith(
                    height: 1.6,
                    color: colorScheme.onSurface.withValues(alpha: Opacities.highOpaque),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 构建备份进度
  Widget _buildBackupProgress(BuildContext context, double backupProgress) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      width: double.infinity,
      padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingL, vertical: Dimensions.spacingM),
      decoration: BoxDecoration(
        color: colorScheme.primaryContainer.withValues(alpha: Opacities.high),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: colorScheme.primary.withValues(alpha: Opacities.high), width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              SizedBox(
                width: Dimensions.spacingL,
                height: Dimensions.spacingL,
                child: CircularProgressIndicator(
                  strokeWidth: 2.5,
                  valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
                ),
              ),
              Dimensions.horizontalSpacerM,
              Text(
                '正在备份...',
                style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.primary),
              ),
            ],
          ),
          Dimensions.verticalSpacerL,
          LinearProgressIndicator(
            value: backupProgress,
            backgroundColor: colorScheme.primaryContainer.withValues(alpha: Opacities.medium),
            valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
            borderRadius: BorderRadius.circular(Dimensions.radiusXs),
          ),
          Dimensions.verticalSpacerS,
          Text(
            '请勿关闭应用或离开此页面',
            style: textTheme.bodySmall?.copyWith(
              color: colorScheme.onSurface.withValues(alpha: Opacities.higherOpaque),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建目录卡片
  Widget _buildDirectoryCard(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final cardColor = Colors.teal;

    return Container(
      width: double.infinity,
      padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingL, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: cardColor.withValues(alpha: Opacities.low),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: cardColor.withValues(alpha: Opacities.mediumHigh), width: 1.5),
      ),
      child: Row(
        children: [
          // 48x48 图标容器
          Container(
            width: Dimensions.iconSizeXxl,
            height: Dimensions.iconSizeXxl,
            decoration: BoxDecoration(
              color: cardColor.withValues(alpha: Opacities.mediumLow),
              borderRadius: BorderRadius.circular(Dimensions.radiusS),
            ),
            child: Icon(Icons.folder_rounded, size: Dimensions.iconSizeXl - 6, color: cardColor),
          ),
          Dimensions.horizontalSpacerM,
          // 路径文本
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '备份位置',
                  style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: cardColor),
                ),
                Dimensions.verticalSpacerXs,
                Text(
                  controller.backupDirectory.value,
                  style: textTheme.bodySmall?.copyWith(color: cardColor.withValues(alpha: Opacities.highOpaque)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          Dimensions.horizontalSpacerS,
          // 修改按钮
          InkWell(
            onTap: () => controller.selectBackupDirectory(),
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            child: Container(
              padding: Dimensions.paddingS,
              decoration: BoxDecoration(
                color: cardColor.withValues(alpha: Opacities.mediumLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
              ),
              child: Icon(Icons.edit_outlined, size: Dimensions.iconSizeM, color: cardColor),
            ),
          ),
        ],
      ),
    );
  }

  /// 构建操作按钮
  Widget _buildActionButton(
    BuildContext context, {
    required String title,
    String? subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: Container(
        width: double.infinity,
        padding: EdgeInsets.all(Dimensions.spacingM + 2),
        decoration: BoxDecoration(
          color: color.withValues(alpha: Opacities.low),
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          border: Border.all(color: color.withValues(alpha: Opacities.mediumHigh), width: 1.5),
        ),
        child: Row(
          children: [
            Container(
              width: Dimensions.iconSizeXxl,
              height: Dimensions.iconSizeXxl,
              decoration: BoxDecoration(
                color: color.withValues(alpha: Opacities.mediumLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
              ),
              child: Icon(icon, size: Dimensions.iconSizeXl - 6, color: color),
            ),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: color),
                  ),
                  if (subtitle != null) ...[
                    Dimensions.verticalSpacerXs,
                    Text(
                      subtitle,
                      style: textTheme.bodySmall?.copyWith(color: color.withValues(alpha: Opacities.highOpaque)),
                    ),
                  ],
                ],
              ),
            ),
            Icon(
              Icons.chevron_right_rounded,
              size: Dimensions.iconSizeM + 2,
              color: color.withValues(alpha: Opacities.higherOpaque),
            ),
          ],
        ),
      ),
    );
  }

  /// 构建分区标题
  Widget _buildSectionTitle(BuildContext context, String title, IconData icon) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Row(
      children: [
        Icon(icon, size: Dimensions.iconSizeM, color: colorScheme.primary),
        Dimensions.horizontalSpacerS,
        Text(
          title,
          style: textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w600,
            color: colorScheme.onSurface.withValues(alpha: Opacities.veryLowOpaque),
          ),
        ),
      ],
    );
  }

  /// 处理备份按钮点击
  void _onBackupPressed(BuildContext context) async {
    final result = await controller.performBackup();

    if (result) {
      UIUtils.showSuccess('备份成功！数据已保存至指定目录');
    } else {
      UIUtils.showError('备份失败，请稍后重试');
    }
  }
}
