import 'package:daily_satori/app/styles/colors.dart';
import 'package:flutter/material.dart';

import 'package:flutter_settings_screens/flutter_settings_screens.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/global.dart';

import '../controllers/settings_controller.dart';

class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      appBar: AppBar(title: const Text('设置'), centerTitle: true, elevation: 0),
      body: Container(
        color: colorScheme.background,
        child: SingleChildScrollView(
          child: Column(
            children: [
              _buildHeader(context),
              const SizedBox(height: 8),
              _buildOpenAIGroup(context),
              _buildPluginGroup(context),
              _buildWebServerGroup(context),
              _buildBackupDirSelect(context),
              _buildFreeSpaceAction(context),
              _buildUpgradeAction(context),
              const SizedBox(height: 24),
              _buildFooter(context),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(color: AppColors.primary(context), borderRadius: BorderRadius.circular(15)),
            child: const Icon(Icons.settings, color: Colors.white, size: 30),
          ),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Daily Satori',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
              ),
              const SizedBox(height: 4),
              Text('个性化您的阅读体验', style: TextStyle(fontSize: 14, color: AppColors.textSecondary(context))),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFooter(BuildContext context) {
    return Center(child: Text('版本 1.0.0', style: TextStyle(fontSize: 12, color: AppColors.textSecondary(context))));
  }

  Widget _buildSettingsCard(BuildContext context, Widget child) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).brightness == Brightness.dark ? AppColors.cardBackgroundDark : Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 5, offset: const Offset(0, 2))],
      ),
      child: child,
    );
  }

  Widget _buildWebServerGroup(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: 'Web 服务器',
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        subtitle: '配置Web访问相关设置',
        subtitleTextStyle: TextStyle(fontSize: 12, color: AppColors.textSecondary(context)),
        children: [
          Obx(
            () => SimpleSettingsTile(
              title: '局域网访问地址',
              subtitle: controller.webServiceAddress.value,
              leading: Icon(Icons.lan, color: AppColors.primary(context)),
              enabled: true,
              showDivider: true,
              onTap: () {
                controller.copyWebServiceAddress();
              },
            ),
          ),
          Obx(
            () => SimpleSettingsTile(
              title: '公网访问地址',
              subtitle: controller.webAccessUrl.value,
              leading: Icon(Icons.public, color: AppColors.primary(context)),
              enabled: true,
              showDivider: true,
              onTap: () {
                controller.copyWebAccessUrl();
              },
            ),
          ),
          TextInputSettingsTile(
            settingKey: SettingService.webServerPasswordKey,
            title: 'Web 服务器密码',
            helperText: '用于web访问的时候鉴权',
          ),
          TextInputSettingsTile(
            settingKey: SettingService.webSocketUrlKey,
            title: 'WebSocket 代理地址',
            helperText: '例如 ws://10.0.2.2:3000/ws',
          ),
        ],
      ),
    );
  }

  Widget _buildPluginGroup(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: '插件',
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        subtitle: '配置插件相关设置',
        subtitleTextStyle: TextStyle(fontSize: 12, color: AppColors.textSecondary(context)),
        children: [
          TextInputSettingsTile(
            settingKey: SettingService.pluginKey,
            title: '插件地址',
            helperText: '例如 https://raw.githubusercontent.com/SatoriTours/plugin/refs/heads/main',
          ),
        ],
      ),
    );
  }

  Widget _buildBackupDirSelect(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: '备份与恢复',
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        subtitle: '管理您的数据备份',
        subtitleTextStyle: TextStyle(fontSize: 12, color: AppColors.textSecondary(context)),
        children: [
          SimpleSettingsTile(
            title: '选择备份路径',
            subtitle: '设置数据备份的存储位置',
            leading: Icon(Icons.folder_open_outlined, color: AppColors.primary(context)),
            showDivider: true,
            onTap: () {
              controller.selectBackupDirectory();
            },
          ),
          SimpleSettingsTile(
            title: '立即备份',
            subtitle: '创建当前数据的备份',
            leading: Icon(Icons.backup_table_outlined, color: AppColors.primary(context)),
            showDivider: true,
            onTap: () async {
              showFullScreenLoading();
              await BackupService.i.checkAndBackup(immediateBackup: true);
              Get.close();
              successNotice("备份成功");
            },
          ),
          SimpleSettingsTile(
            title: '从备份恢复',
            subtitle: '从之前的备份中恢复数据',
            leading: Icon(Icons.restore_rounded, color: AppColors.primary(context)),
            showDivider: false,
            onTap: () {
              Get.toNamed(Routes.BACKUP_RESTORE);
            },
          ),
        ],
      ),
    );
  }

  Widget _buildFreeSpaceAction(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: '清理与维护',
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        subtitle: '优化应用性能和存储空间',
        subtitleTextStyle: TextStyle(fontSize: 12, color: AppColors.textSecondary(context)),
        children: [
          SimpleSettingsTile(
            title: '清除重复的图片',
            subtitle: '删除重复的图片文件以节省空间',
            leading: Icon(Icons.cleaning_services, color: AppColors.primary(context)),
            showDivider: true,
            onTap: () async {
              showFullScreenLoading();
              await FreeDiskService.i.clean();
              Get.close();
              successNotice("清除重复图片完成");
            },
          ),
          SimpleSettingsTile(
            title: '清空所有标签',
            subtitle: '删除所有已创建的标签',
            leading: Icon(Icons.clean_hands, color: AppColors.primary(context)),
            showDivider: false,
            onTap: () async {
              showFullScreenLoading();
              await TagsService.i.clearAllTags();
              Get.offNamed(Routes.ARTICLES);
              successNotice("清除标签完成");
            },
          ),
        ],
      ),
    );
  }

  Widget _buildUpgradeAction(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: '关于应用',
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        subtitle: '应用信息与更新',
        subtitleTextStyle: TextStyle(fontSize: 12, color: AppColors.textSecondary(context)),
        children: [
          SimpleSettingsTile(
            title: '检查更新',
            subtitle: '获取最新版本的应用',
            leading: Icon(Icons.system_update, color: AppColors.primary(context)),
            showDivider: false,
            onTap: () {
              AppUpgradeService.i.checkAndDownload();
            },
          ),
        ],
      ),
    );
  }

  Widget _buildOpenAIGroup(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: 'AI 设置',
        titleTextStyle: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppColors.primary(context)),
        subtitle: '配置AI相关功能',
        subtitleTextStyle: TextStyle(fontSize: 12, color: AppColors.textSecondary(context)),
        children: [
          TextInputSettingsTile(
            settingKey: SettingService.openAIAddressKey,
            title: 'API 地址',
            helperText: '例如: https://api.openai.com/v1/ (OpenAI 或 DeepSeek)',
          ),
          TextInputSettingsTile(
            settingKey: SettingService.openAITokenKey,
            title: 'API Token',
            helperText: '输入OpenAI or DeepSeek token',
          ),
          TextInputSettingsTile(
            settingKey: SettingService.aiModelKey,
            title: '模型名称',
            helperText: '例如 deepseek-v3 或者 gpt-4o-mini',
          ),
        ],
      ),
    );
  }
}
