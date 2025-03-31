import 'package:daily_satori/app/styles/colors.dart';
import 'package:flutter/material.dart';

import 'package:flutter_settings_screens/flutter_settings_screens.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/app_styles.dart';
import 'package:daily_satori/app/styles/component_style.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/app/utils/utils.dart';

import '../controllers/settings_controller.dart';

class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: colorScheme.background,
      appBar: AppBar(
        title: const Text('设置'),
        centerTitle: true,
        elevation: 0,
        backgroundColor: colorScheme.surface,
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline),
            onPressed: () {
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
                children: [const SizedBox(height: 16), const Text('您的个性化阅读助手，支持文章收藏、AI内容分析和日记管理。')],
              );
            },
          ),
        ],
      ),
      body: Obx(
        () =>
            controller.isLoading.value
                ? const Center(child: CircularProgressIndicator())
                : CustomScrollView(
                  physics: const BouncingScrollPhysics(),
                  slivers: [
                    SliverToBoxAdapter(child: _buildUserHeader(context)),
                    SliverList(
                      delegate: SliverChildListDelegate([
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 300),
                          child: _buildFeaturesSection(context),
                        ),
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 300),
                          child: _buildDataSection(context),
                        ),
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 300),
                          child: _buildSystemSection(context),
                        ),
                        const SizedBox(height: 20),
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 300),
                          child: _buildVersionInfo(context),
                        ),
                        const SizedBox(height: 40),
                      ]),
                    ),
                  ],
                ),
      ),
    );
  }

  Widget _buildUserHeader(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 30, 20, 25),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: colorScheme.surface,
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Column(
        children: [
          Row(
            children: [
              Hero(
                tag: 'app_logo',
                child: Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    color: colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: [
                      BoxShadow(
                        color: colorScheme.primary.withOpacity(0.2),
                        blurRadius: 10,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Icon(Icons.article_rounded, color: colorScheme.primary, size: 32),
                ),
              ),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Daily Satori',
                      style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: colorScheme.onSurface),
                    ),
                    const SizedBox(height: 4),
                    Text('个性化您的阅读体验', style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withOpacity(0.7))),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildVersionInfo(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    return Center(
      child: Column(
        children: [
          Obx(
            () => Text(
              '版本 ${controller.appVersion.value}',
              style: TextStyle(fontSize: 13, color: colorScheme.onBackground.withOpacity(0.6)),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '© 2023-2024 Satori Tours',
            style: TextStyle(fontSize: 12, color: colorScheme.onBackground.withOpacity(0.5)),
          ),
        ],
      ),
    );
  }

  Widget _buildFeaturesSection(BuildContext context) {
    return _buildSection(
      context,
      title: '功能',
      icon: Icons.flash_on_rounded,
      children: [
        _buildSettingTile(
          context,
          title: 'AI配置管理',
          subtitle: '管理AI模型配置，优化摘要、内容解读和日记生成',
          icon: Icons.smart_toy_rounded,
          iconBackground: Colors.blue,
          onTap: () => Get.toNamed(Routes.AI_CONFIG),
        ),
        _buildSettingTile(
          context,
          title: '插件中心',
          subtitle: '管理扩展插件与订阅源',
          icon: Icons.extension_rounded,
          iconBackground: Colors.deepPurple,
          onTap: () => _showPluginUrlDialog(context),
        ),
      ],
    );
  }

  Widget _buildDataSection(BuildContext context) {
    return _buildSection(
      context,
      title: '数据',
      icon: Icons.storage_rounded,
      children: [
        _buildSettingTile(
          context,
          title: '备份与恢复',
          subtitle: '保护您的数据安全',
          icon: Icons.backup_rounded,
          iconBackground: Colors.green,
          onTap: () => _showBackupDialog(context),
        ),
        _buildSettingTile(
          context,
          title: '清理与维护',
          subtitle: '优化应用性能与存储空间',
          icon: Icons.cleaning_services_rounded,
          iconBackground: Colors.orange,
          onTap: () => _showCleanupDialog(context),
        ),
      ],
    );
  }

  Widget _buildSystemSection(BuildContext context) {
    return _buildSection(
      context,
      title: '系统',
      icon: Icons.settings_rounded,
      children: [
        _buildSettingTile(
          context,
          title: 'Web服务器',
          subtitle: '配置远程访问与代理设置',
          icon: Icons.language_rounded,
          iconBackground: Colors.teal,
          onTap: () => _showWebServerDialog(context),
        ),
        _buildSettingTile(
          context,
          title: '检查更新',
          subtitle: '获取最新版本',
          icon: Icons.system_update_rounded,
          iconBackground: Colors.indigo,
          onTap: () => AppUpgradeService.i.checkAndDownload(),
        ),
      ],
    );
  }

  Widget _buildSection(
    BuildContext context, {
    required String title,
    required IconData icon,
    required List<Widget> children,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
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
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: colorScheme.surface,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.03), blurRadius: 10, offset: const Offset(0, 5))],
            ),
            child: Column(children: _addDividers(children)),
          ),
        ],
      ),
    );
  }

  List<Widget> _addDividers(List<Widget> children) {
    if (children.isEmpty) return children;

    List<Widget> result = [];
    for (int i = 0; i < children.length; i++) {
      result.add(children[i]);
      if (i < children.length - 1) {
        result.add(const Divider(height: 1, indent: 72));
      }
    }
    return result;
  }

  Widget _buildSettingTile(
    BuildContext context, {
    required String title,
    required String subtitle,
    required IconData icon,
    required VoidCallback onTap,
    Color? iconBackground,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: (iconBackground ?? colorScheme.primaryContainer).withOpacity(0.8),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: Colors.white, size: 22),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: colorScheme.onSurface),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withOpacity(0.6)),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              Icon(Icons.navigate_next_rounded, color: colorScheme.onSurface.withOpacity(0.3), size: 24),
            ],
          ),
        ),
      ),
    );
  }

  void _showPluginUrlDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textController = TextEditingController(text: Settings.getValue(SettingService.pluginKey) ?? '');

    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            title: Row(
              children: [
                Icon(Icons.extension_rounded, color: colorScheme.primary, size: 24),
                const SizedBox(width: 12),
                const Text('插件地址设置'),
              ],
            ),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: textController,
                  decoration: InputDecoration(
                    labelText: '插件地址',
                    hintText: 'https://raw.githubusercontent.com/SatoriTours/plugin/refs/heads/main',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    prefixIcon: const Icon(Icons.link_rounded),
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: Text('取消', style: TextStyle(color: colorScheme.onSurface.withOpacity(0.7))),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  backgroundColor: colorScheme.primary,
                ),
                onPressed: () {
                  Settings.setValue(SettingService.pluginKey, textController.text);
                  Navigator.of(context).pop();
                  UIUtils.showSuccess('设置已保存');
                },
                child: const Text('保存'),
              ),
            ],
          ),
    );
  }

  void _showBackupDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder:
          (context) => SafeArea(
            child: Container(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.backup_rounded, color: colorScheme.primary, size: 24),
                      const SizedBox(width: 12),
                      Text(
                        '备份与恢复',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: colorScheme.onSurface),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '保护您的数据安全，定期备份可避免数据丢失',
                    style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withOpacity(0.6)),
                  ),
                  const SizedBox(height: 20),
                  _buildActionTile(
                    context: context,
                    title: '选择备份路径',
                    subtitle: '设置数据备份的存储位置',
                    icon: Icons.folder_open_rounded,
                    color: Colors.blue,
                    onTap: () {
                      Navigator.of(context).pop();
                      controller.selectBackupDirectory();
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildActionTile(
                    context: context,
                    title: '立即备份',
                    subtitle: '创建当前数据的备份',
                    icon: Icons.save_rounded,
                    color: Colors.green,
                    onTap: () async {
                      Navigator.of(context).pop();
                      UIUtils.showLoading();
                      await BackupService.i.checkAndBackup(immediateBackup: true);
                      Get.back();
                      UIUtils.showSuccess("备份成功");
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildActionTile(
                    context: context,
                    title: '从备份恢复',
                    subtitle: '从之前的备份中恢复数据',
                    icon: Icons.restore_rounded,
                    color: Colors.orange,
                    onTap: () {
                      Navigator.of(context).pop();
                      Get.toNamed(Routes.BACKUP_RESTORE);
                    },
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            ),
          ),
    );
  }

  void _showCleanupDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder:
          (context) => SafeArea(
            child: Container(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.cleaning_services_rounded, color: colorScheme.primary, size: 24),
                      const SizedBox(width: 12),
                      Text(
                        '清理与维护',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: colorScheme.onSurface),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '优化应用性能与存储空间，保持流畅体验',
                    style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withOpacity(0.6)),
                  ),
                  const SizedBox(height: 20),
                  _buildActionTile(
                    context: context,
                    title: '重新用AI分析所有文章',
                    subtitle: '对收藏的文章重新进行AI分析处理',
                    icon: Icons.refresh_rounded,
                    color: Colors.indigo,
                    onTap: () async {
                      Navigator.of(context).pop();
                      UIUtils.showLoading();
                      controller.reAnalyzeAllWebpages();
                      Get.offNamed(Routes.ARTICLES);
                      UIUtils.showSuccess("操作完成");
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildActionTile(
                    context: context,
                    title: '清除重复的图片',
                    subtitle: '删除重复的图片文件以节省存储空间',
                    icon: Icons.image_not_supported_rounded,
                    color: Colors.deepPurple,
                    onTap: () async {
                      Navigator.of(context).pop();
                      UIUtils.showLoading();
                      await FreeDiskService.i.clean();
                      Get.back();
                      UIUtils.showSuccess("清除重复图片完成");
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildActionTile(
                    context: context,
                    title: '清空所有标签',
                    subtitle: '删除所有已创建的标签',
                    icon: Icons.label_off_rounded,
                    color: Colors.red,
                    onTap: () async {
                      Navigator.of(context).pop();
                      UIUtils.showLoading();
                      TagRepository.removeAll();
                      Get.offNamed(Routes.ARTICLES);
                      UIUtils.showSuccess("清除标签完成");
                    },
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            ),
          ),
    );
  }

  void _showWebServerDialog(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final passwordController = TextEditingController(
      text: Settings.getValue(SettingService.webServerPasswordKey) ?? '',
    );
    final wsUrlController = TextEditingController(text: Settings.getValue(SettingService.webSocketUrlKey) ?? '');

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder:
          (context) => SafeArea(
            child: SingleChildScrollView(
              padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 20, right: 20, top: 20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.language_rounded, color: colorScheme.primary, size: 24),
                      const SizedBox(width: 12),
                      Text(
                        'Web服务器设置',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: colorScheme.onSurface),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text('配置远程访问与代理设置', style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withOpacity(0.6))),
                  const SizedBox(height: 20),
                  _buildServerInfoTile(
                    context: context,
                    title: '局域网访问地址',
                    value: controller.webServiceAddress.value,
                    icon: Icons.lan_rounded,
                    color: Colors.teal,
                    onTap: () {
                      controller.copyWebServiceAddress();
                      UIUtils.showSuccess("已复制到剪贴板");
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildServerInfoTile(
                    context: context,
                    title: '公网访问地址',
                    value: controller.webAccessUrl.value,
                    icon: Icons.public_rounded,
                    color: Colors.blue,
                    onTap: () {
                      controller.copyWebAccessUrl();
                      UIUtils.showSuccess("已复制到剪贴板");
                    },
                  ),
                  const SizedBox(height: 20),
                  TextField(
                    controller: passwordController,
                    decoration: InputDecoration(
                      labelText: 'Web 服务器密码',
                      helperText: '用于web访问的时候鉴权',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      prefixIcon: const Icon(Icons.password_rounded),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: wsUrlController,
                    decoration: InputDecoration(
                      labelText: 'WebSocket 代理地址',
                      helperText: '例如 ws://10.0.2.2:3000/ws',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      prefixIcon: const Icon(Icons.swap_horiz_rounded),
                    ),
                  ),
                  const SizedBox(height: 24),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      TextButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: Text('取消', style: TextStyle(color: colorScheme.onSurface.withOpacity(0.7))),
                      ),
                      const SizedBox(width: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          backgroundColor: colorScheme.primary,
                          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                        ),
                        onPressed: () {
                          Settings.setValue(SettingService.webServerPasswordKey, passwordController.text);
                          Settings.setValue(SettingService.webSocketUrlKey, wsUrlController.text);
                          Navigator.of(context).pop();
                          UIUtils.showSuccess('设置已保存');
                        },
                        child: const Text('保存'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                ],
              ),
            ),
          ),
    );
  }

  Widget _buildActionTile({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            border: Border.all(color: colorScheme.outline.withOpacity(0.1)),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(color: color.withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
                child: Icon(icon, color: color, size: 22),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: colorScheme.onSurface),
                    ),
                    const SizedBox(height: 2),
                    Text(subtitle, style: TextStyle(fontSize: 14, color: colorScheme.onSurface.withOpacity(0.6))),
                  ],
                ),
              ),
              Icon(Icons.arrow_forward_ios_rounded, size: 16, color: colorScheme.onSurface.withOpacity(0.4)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildServerInfoTile({
    required BuildContext context,
    required String title,
    required String value,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            border: Border.all(color: colorScheme.outline.withOpacity(0.1)),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(color: color.withOpacity(0.1), borderRadius: BorderRadius.circular(12)),
                child: Icon(icon, color: color, size: 22),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: colorScheme.onSurface),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      value.isEmpty ? '未设置' : value,
                      style: TextStyle(
                        fontSize: 14,
                        color:
                            value.isEmpty ? colorScheme.error.withOpacity(0.7) : colorScheme.onSurface.withOpacity(0.6),
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: colorScheme.primary.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(Icons.copy_rounded, size: 18, color: colorScheme.primary),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
