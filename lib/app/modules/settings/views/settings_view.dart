import 'package:daily_satori/app/components/common/feature_icon.dart';
import 'package:daily_satori/app/styles/app_styles.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import '../controllers/settings_controller.dart';

/// 设置页面
/// 包含三个主要部分：
/// 1. 功能设置：AI配置、插件管理等
/// 2. 数据管理：备份恢复、清理维护等
/// 3. 系统设置：Web服务器、版本更新等
class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.getColorScheme(context).surface,
      appBar: AppBar(
        title: const Text('设置'),
        centerTitle: true,
        elevation: 0,
        backgroundColor: AppTheme.getColorScheme(context).surface,
        actions: [
          IconButton(icon: const Icon(Icons.info_outline), tooltip: '关于', onPressed: () => _showAboutDialog(context)),
        ],
      ),
      body: Obx(() => controller.isLoading.value ? AppStyles.loadingState(context) : _buildSettingsList(context)),
    );
  }

  /// 显示关于对话框
  void _showAboutDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    showAboutDialog(
      context: context,
      applicationName: 'Daily Satori',
      applicationVersion: 'v${controller.appVersion.value}',
      applicationIcon: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(color: colorScheme.primary, borderRadius: BorderRadius.circular(10)),
        child: Icon(Icons.article, color: colorScheme.onPrimary),
      ),
      children: [Dimensions.verticalSpacerM, Text('您的个性化阅读助手，支持文章收藏、AI内容分析和日记管理。', style: textTheme.bodyMedium)],
    );
  }

  /// 构建设置列表
  Widget _buildSettingsList(BuildContext context) {
    return ListView(
      physics: const BouncingScrollPhysics(),
      padding: Dimensions.paddingM,
      children: [
        _buildFunctionSection(context),
        _buildDataSection(context),
        _buildSystemSection(context),
        Dimensions.verticalSpacerL,
        _buildVersionInfo(context),
        Dimensions.verticalSpacerL,
      ],
    );
  }

  /// 构建功能设置分区
  Widget _buildFunctionSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '功能',
      icon: Icons.flash_on_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: 'AI 配置',
          subtitle: '设置多个场景下的 AI 模型',
          icon: Icons.smart_toy_rounded,
          color: Colors.blue,
          onTap: () => Get.toNamed('/ai-config'),
        ),
        _buildSettingItem(
          context: context,
          title: '插件中心',
          subtitle: '管理扩展插件与订阅源',
          icon: Icons.extension_rounded,
          color: Colors.deepPurple,
          onTap: () => Get.toNamed(Routes.pluginCenter),
        ),
      ],
    );
  }

  /// 构建数据管理分区
  Widget _buildDataSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '数据',
      icon: Icons.storage_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: '备份与恢复',
          subtitle: '保护您的数据安全',
          icon: Icons.backup_rounded,
          color: Colors.green,
          onTap: () => Get.toNamed(Routes.backupSettings),
        ),
        _buildSettingItem(
          context: context,
          title: '清理与维护',
          subtitle: '优化应用性能与存储空间',
          icon: Icons.cleaning_services_rounded,
          color: Colors.orange,
          onTap: () => _showCleanupDialog(context),
        ),
      ],
    );
  }

  /// 构建系统设置分区
  Widget _buildSystemSection(BuildContext context) {
    return _buildSettingsSection(
      context: context,
      title: '系统',
      icon: Icons.settings_rounded,
      items: [
        _buildSettingItem(
          context: context,
          title: 'Web服务器',
          subtitle: '配置远程访问与代理设置',
          icon: Icons.language_rounded,
          color: Colors.teal,
          onTap: () => _showWebServerDialog(context),
        ),
        _buildSettingItem(
          context: context,
          title: '检查更新',
          subtitle: '获取最新版本',
          icon: Icons.system_update_rounded,
          color: Colors.indigo,
          onTap: () => AppUpgradeService.i.checkAndDownload(),
        ),
      ],
    );
  }

  /// 构建设置分区
  Widget _buildSettingsSection({
    required BuildContext context,
    required String title,
    required IconData icon,
    required List<Widget> items,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 分区标题
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                Icon(icon, size: 16, color: colorScheme.primary),
                Dimensions.horizontalSpacerS,
                Text(
                  title,
                  style: textTheme.labelMedium?.copyWith(
                    color: colorScheme.primary,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 0.2,
                  ),
                ),
              ],
            ),
          ),
          // 分区内容
          Card(
            margin: EdgeInsets.zero,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Dimensions.radiusM)),
            child: Column(children: items),
          ),
        ],
      ),
    );
  }

  /// 构建设置项
  Widget _buildSettingItem({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        child: Row(
          children: [
            FeatureIcon(icon: icon, iconColor: color, containerSize: 32, iconSize: 16),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: textTheme.titleSmall),
                  Text(subtitle, style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withAlpha(179))),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: colorScheme.onSurface.withAlpha(77), size: 18),
          ],
        ),
      ),
    );
  }

  /// 构建版本信息
  Widget _buildVersionInfo(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Center(
      child: Column(
        children: [
          Obx(
            () => Text(
              '版本 ${controller.appVersion.value}',
              style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.6)),
            ),
          ),
          Dimensions.verticalSpacerS,
          Text(
            '© 2023-2024 Satori Tours',
            style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.5), fontSize: 11),
          ),
        ],
      ),
    );
  }

  // 对话框显示方法
  void _showCleanupDialog(BuildContext context) {
    // TODO: 实现清理维护对话框
  }

  void _showWebServerDialog(BuildContext context) {
    // TODO: 实现Web服务器配置对话框
  }
}
