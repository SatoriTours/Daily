import 'package:daily_satori/app/styles/colors.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

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
      appBar: _buildAppBar(context),
      body: Obx(
        () =>
            controller.isLoading.value ? const Center(child: CircularProgressIndicator()) : _buildSettingsList(context),
      ),
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return AppBar(
      title: const Text('设置'),
      centerTitle: true,
      elevation: 0,
      backgroundColor: colorScheme.surface,
      actions: [IconButton(icon: const Icon(Icons.info_outline), onPressed: () => _showAboutDialog(context))],
    );
  }

  /// 显示关于对话框
  void _showAboutDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    showAboutDialog(
      context: context,
      applicationName: 'Daily Satori',
      applicationVersion: 'v${controller.appVersion.value}',
      applicationIcon: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(color: AppColors.primary(context), borderRadius: BorderRadius.circular(10)),
        child: Icon(Icons.article, color: colorScheme.onPrimary),
      ),
      children: const [SizedBox(height: 16), Text('您的个性化阅读助手，支持文章收藏、AI内容分析和日记管理。')],
    );
  }

  /// 构建设置列表
  Widget _buildSettingsList(BuildContext context) {
    return CustomScrollView(
      physics: const BouncingScrollPhysics(),
      slivers: [
        SliverList(
          delegate: SliverChildListDelegate([
            const SizedBox(height: 16),
            _SettingsSection(
              title: '功能',
              icon: Icons.flash_on_rounded,
              items: [
                _SettingItem(
                  title: 'AI配置管理',
                  subtitle: '管理AI模型配置，优化摘要、内容解读和日记生成',
                  icon: Icons.smart_toy_rounded,
                  iconBackground: Colors.blue,
                  onTap: () => Get.toNamed('/ai-config'),
                ),
                _SettingItem(
                  title: '插件中心',
                  subtitle: '管理扩展插件与订阅源',
                  icon: Icons.extension_rounded,
                  iconBackground: Colors.deepPurple,
                  onTap: () => _showPluginDialog(context),
                ),
              ],
            ),
            _SettingsSection(
              title: '数据',
              icon: Icons.storage_rounded,
              items: [
                _SettingItem(
                  title: '备份与恢复',
                  subtitle: '保护您的数据安全',
                  icon: Icons.backup_rounded,
                  iconBackground: Colors.green,
                  onTap: () => _showBackupDialog(context),
                ),
                _SettingItem(
                  title: '清理与维护',
                  subtitle: '优化应用性能与存储空间',
                  icon: Icons.cleaning_services_rounded,
                  iconBackground: Colors.orange,
                  onTap: () => _showCleanupDialog(context),
                ),
              ],
            ),
            _SettingsSection(
              title: '系统',
              icon: Icons.settings_rounded,
              items: [
                _SettingItem(
                  title: 'Web服务器',
                  subtitle: '配置远程访问与代理设置',
                  icon: Icons.language_rounded,
                  iconBackground: Colors.teal,
                  onTap: () => _showWebServerDialog(context),
                ),
                _SettingItem(
                  title: '检查更新',
                  subtitle: '获取最新版本',
                  icon: Icons.system_update_rounded,
                  iconBackground: Colors.indigo,
                  onTap: () => AppUpgradeService.i.checkAndDownload(),
                ),
              ],
            ),
            const SizedBox(height: 20),
            _buildVersionInfo(context),
            const SizedBox(height: 40),
          ]),
        ),
      ],
    );
  }

  /// 构建版本信息
  Widget _buildVersionInfo(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return Center(
      child: Column(
        children: [
          Obx(
            () => Text(
              '版本 ${controller.appVersion.value}',
              style: TextStyle(fontSize: 13, color: colorScheme.onSurface.withValues(alpha: 0.6)),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '© 2023-2024 Satori Tours',
            style: TextStyle(fontSize: 12, color: colorScheme.onSurface.withValues(alpha: 0.5)),
          ),
        ],
      ),
    );
  }

  // 对话框显示方法
  void _showPluginDialog(BuildContext context) {
    // TODO: 实现插件管理对话框
  }

  void _showBackupDialog(BuildContext context) {
    // TODO: 实现备份恢复对话框
  }

  void _showCleanupDialog(BuildContext context) {
    // TODO: 实现清理维护对话框
  }

  void _showWebServerDialog(BuildContext context) {
    // TODO: 实现Web服务器配置对话框
  }
}

/// 设置项组件
class _SettingItem extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color iconBackground;
  final VoidCallback onTap;

  const _SettingItem({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.iconBackground,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: iconBackground.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: iconBackground),
      ),
      title: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
      subtitle: Text(subtitle, style: TextStyle(fontSize: 13, color: colorScheme.onSurface.withValues(alpha: 0.6))),
      trailing: Icon(Icons.chevron_right, color: colorScheme.onSurface.withValues(alpha: 0.3)),
      onTap: onTap,
    );
  }
}

/// 设置分区组件
class _SettingsSection extends StatelessWidget {
  final String title;
  final IconData icon;
  final List<_SettingItem> items;

  const _SettingsSection({required this.title, required this.icon, required this.items});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 分区标题
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Row(
              children: [
                Icon(icon, size: 18, color: colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: colorScheme.primary,
                    letterSpacing: 0.3,
                  ),
                ),
              ],
            ),
          ),
          // 分区内容
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: colorScheme.surface,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(color: Colors.black.withValues(alpha: 0.03), blurRadius: 10, offset: const Offset(0, 5)),
              ],
            ),
            child: Column(
              children:
                  items.asMap().entries.map((entry) {
                    final index = entry.key;
                    final item = entry.value;
                    return Column(children: [item, if (index < items.length - 1) const Divider(height: 1, indent: 72)]);
                  }).toList(),
            ),
          ),
        ],
      ),
    );
  }
}
