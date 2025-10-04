import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';
import 'package:daily_satori/app/styles/components/button_styles.dart';

import '../controllers/backup_settings_controller.dart';

class BackupSettingsView extends GetView<BackupSettingsController> {
  const BackupSettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: AppBar(title: const Text('备份与恢复'), centerTitle: true, backgroundColor: colorScheme.surface),
      body: Obx(() {
        // 如果还没有选择备份目录，直接显示提示选择
        if (controller.backupDirectory.value.isEmpty) {
          return _buildSelectDirectoryPrompt(context);
        }

        // 已有备份目录，显示正常界面
        return _buildMainContent(context);
      }),
    );
  }

  /// 构建选择目录提示
  Widget _buildSelectDirectoryPrompt(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.folder_open_rounded, size: 64, color: colorScheme.primary.withValues(alpha: 0.8)),
          const SizedBox(height: 24),
          Text(
            '请选择备份目录',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500, color: colorScheme.onSurface),
          ),
          const SizedBox(height: 12),
          Text(
            '选择一个文件夹存储您的应用数据备份',
            style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withValues(alpha: 0.6)),
          ),
          const SizedBox(height: 32),
          ElevatedButton.icon(
            onPressed: () => controller.selectBackupDirectory(),
            icon: const Icon(Icons.create_new_folder_rounded),
            label: const Text('选择备份目录'),
            style: ButtonStyles.getPrimaryStyle(context),
          ),
        ],
      ),
    );
  }

  /// 构建主要内容
  Widget _buildMainContent(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 备份目录区域
          _buildSectionTitle(context, '备份目录'),
          const SizedBox(height: 12),
          _buildDirectoryCard(context),
          const SizedBox(height: 32),

          // 操作按钮区域
          _buildSectionTitle(context, '操作'),
          const SizedBox(height: 12),

          // 立即备份按钮
          Obx(
            () => controller.isBackingUp.value
                ? _buildBackupProgress(context)
                : _buildActionButton(
                    context,
                    title: '立即备份',
                    icon: Icons.backup_rounded,
                    color: colorScheme.primary,
                    onTap: () => _onBackupPressed(context),
                  ),
          ),
          const SizedBox(height: 16),

          // 恢复备份按钮
          _buildActionButton(
            context,
            title: '恢复备份',
            icon: Icons.restore_rounded,
            color: Colors.deepPurple,
            onTap: () => Get.toNamed(Routes.backupRestore),
          ),

          const SizedBox(height: 24),

          // 提示信息
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: colorScheme.secondaryContainer.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.info_outline, size: 20, color: colorScheme.secondary),
                    const SizedBox(width: 8),
                    Text(
                      '备份说明',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: colorScheme.secondary),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  '· 备份将保存您的全部应用数据，包括文章、笔记和设置\n'
                  '· 备份文件存储在您选择的目录中\n'
                  '· 建议定期备份数据以防丢失',
                  style: TextStyle(fontSize: 14, height: 1.6, color: colorScheme.onSecondaryContainer),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 构建备份进度
  Widget _buildBackupProgress(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.primaryContainer.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
              const SizedBox(width: 12),
              Text(
                '正在备份...',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: colorScheme.primary),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Obx(
            () => LinearProgressIndicator(
              value: controller.backupProgress.value,
              backgroundColor: colorScheme.primaryContainer.withValues(alpha: 0.2),
              valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
            ),
          ),
          const SizedBox(height: 8),
          Text('请勿关闭应用或离开此页面', style: TextStyle(fontSize: 13, color: colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }

  /// 构建目录卡片
  Widget _buildDirectoryCard(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.primary.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: colorScheme.primary.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.folder_rounded, size: 22, color: colorScheme.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '当前备份位置',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500, color: colorScheme.onSurface),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: colorScheme.surface,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: colorScheme.primary.withValues(alpha: 0.1)),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    controller.backupDirectory.value,
                    style: TextStyle(fontSize: 14, color: colorScheme.onSurfaceVariant),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: () => controller.selectBackupDirectory(),
              icon: const Icon(Icons.edit_rounded, size: 18),
              label: const Text('修改'),
              style: ButtonStyles.getTextStyle(context),
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
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withValues(alpha: 0.2)),
        ),
        child: Row(
          children: [
            Icon(icon, size: 24, color: color),
            const SizedBox(width: 16),
            Text(
              title,
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: color),
            ),
            const Spacer(),
            Icon(Icons.arrow_forward_ios_rounded, size: 16, color: color.withValues(alpha: 0.6)),
          ],
        ),
      ),
    );
  }

  /// 构建分区标题
  Widget _buildSectionTitle(BuildContext context, String title) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: Text(
        title,
        style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: colorScheme.onSurface),
      ),
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
