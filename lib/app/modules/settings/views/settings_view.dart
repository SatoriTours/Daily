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

    return Scaffold(
      appBar: AppBar(title: const Text('设置'), centerTitle: true, elevation: 0),
      body: Container(
        color: colorScheme.surface,
        child: SingleChildScrollView(
          child: Column(
            children: [
              _buildHeader(context),
              Dimensions.verticalSpacerS,
              _buildOpenAIGroup(context),
              _buildPluginGroup(context),
              _buildWebServerGroup(context),
              _buildBackupDirSelect(context),
              _buildFreeSpaceAction(context),
              _buildUpgradeAction(context),
              Dimensions.verticalSpacerL,
              _buildFooter(context),
              Dimensions.verticalSpacerL,
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return ComponentStyle.settingsItemContainer(
      context,
      Row(
        children: [
          Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(
              color: AppColors.primary(context),
              borderRadius: BorderRadius.circular(Dimensions.radiusM),
            ),
            child: Icon(Icons.settings, color: AppTheme.getColorScheme(context).onPrimary, size: Dimensions.iconSizeL),
          ),
          Dimensions.horizontalSpacerM,
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Daily Satori', style: MyFontStyle.listTitleStyleThemed(context)),
              Dimensions.verticalSpacerXs,
              Text('个性化您的阅读体验', style: MyFontStyle.cardSubtitleStyleThemed(context)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFooter(BuildContext context) {
    return FutureBuilder<String>(
      future: AppInfoUtils.getVersion(),
      builder: (context, snapshot) {
        final version = snapshot.hasData ? '版本 ${snapshot.data}' : '版本 获取中...';
        return Center(child: Text(version, style: MyFontStyle.cardSubtitleStyleThemed(context)));
      },
    );
  }

  Widget _buildSettingsCard(BuildContext context, Widget child) {
    return Container(margin: Dimensions.marginCard, decoration: AppStyles.cardDecoration(context), child: child);
  }

  Widget _buildWebServerGroup(BuildContext context) {
    return _buildSettingsCard(
      context,
      SettingsGroup(
        title: 'Web 服务器',
        titleTextStyle: MyFontStyle.settingGroupTitleThemed(context),
        subtitle: '配置Web访问相关设置',
        subtitleTextStyle: MyFontStyle.cardSubtitleStyleThemed(context),
        children: [
          Obx(
            () => SimpleSettingsTile(
              title: '局域网访问地址',
              subtitle: controller.webServiceAddress.value,
              leading: Icon(Icons.lan, color: AppColors.primary(context), size: Dimensions.iconSizeM),
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
              leading: Icon(Icons.public, color: AppColors.primary(context), size: Dimensions.iconSizeM),
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
        titleTextStyle: MyFontStyle.settingGroupTitleThemed(context),
        subtitle: '配置插件相关设置',
        subtitleTextStyle: MyFontStyle.cardSubtitleStyleThemed(context),
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
        titleTextStyle: MyFontStyle.settingGroupTitleThemed(context),
        subtitle: '管理您的数据备份',
        subtitleTextStyle: MyFontStyle.cardSubtitleStyleThemed(context),
        children: [
          SimpleSettingsTile(
            title: '选择备份路径',
            subtitle: '设置数据备份的存储位置',
            leading: Icon(Icons.folder_open_outlined, color: AppColors.primary(context), size: Dimensions.iconSizeM),
            showDivider: true,
            onTap: () {
              controller.selectBackupDirectory();
            },
          ),
          SimpleSettingsTile(
            title: '立即备份',
            subtitle: '创建当前数据的备份',
            leading: Icon(
              Icons.backup_table_outlined,
              color: AppTheme.getColorScheme(context).primary,
              size: Dimensions.iconSizeM,
            ),
            showDivider: true,
            onTap: () async {
              UIUtils.showLoading();
              await BackupService.i.checkAndBackup(immediateBackup: true);
              Get.close();
              UIUtils.showSuccess("备份成功");
            },
          ),
          SimpleSettingsTile(
            title: '从备份恢复',
            subtitle: '从之前的备份中恢复数据',
            leading: Icon(Icons.restore_rounded, color: AppColors.primary(context), size: Dimensions.iconSizeM),
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
        titleTextStyle: MyFontStyle.settingGroupTitleThemed(context),
        subtitle: '优化应用性能和存储空间',
        subtitleTextStyle: MyFontStyle.cardSubtitleStyleThemed(context),
        children: [
          SimpleSettingsTile(
            title: '清除重复的图片',
            subtitle: '删除重复的图片文件以节省空间',
            leading: Icon(
              Icons.cleaning_services,
              color: AppTheme.getColorScheme(context).primary,
              size: Dimensions.iconSizeM,
            ),
            showDivider: true,
            onTap: () async {
              UIUtils.showLoading();
              await FreeDiskService.i.clean();
              Get.close();
              UIUtils.showSuccess("清除重复图片完成");
            },
          ),
          SimpleSettingsTile(
            title: '仅保留文章封面图',
            subtitle: '仅保留每篇文章的第一张图片作为封面图，删除其他图片和截图',
            leading: Icon(
              Icons.image_not_supported_outlined,
              color: AppTheme.getColorScheme(context).primary,
              size: Dimensions.iconSizeM,
            ),
            showDivider: true,
            onTap: () async {
              // 显示确认对话框
              Get.dialog(
                AlertDialog(
                  title: const Text('确认操作'),
                  content: const Text('此操作将删除所有文章中除封面图外的图片和截图，无法恢复。确定要继续吗？'),
                  actions: [
                    TextButton(child: const Text('取消'), onPressed: () => Get.back()),
                    TextButton(
                      child: const Text('确定'),
                      onPressed: () async {
                        Get.back();
                        UIUtils.showLoading(tips: '正在处理图片，请稍等...');
                        try {
                          await controller.migrateArticleImages();
                          Get.close();
                          UIUtils.showSuccess("文章图片迁移完成");
                        } catch (e) {
                          Get.close();
                          UIUtils.showError("操作失败: $e");
                        }
                      },
                    ),
                  ],
                ),
              );
            },
          ),
          SimpleSettingsTile(
            title: '迁移文章封面属性',
            subtitle: '将文章的首张图片设置为封面属性（仅数据迁移，不删除图片）',
            leading: Icon(
              Icons.photo_size_select_actual_outlined,
              color: AppTheme.getColorScheme(context).primary,
              size: Dimensions.iconSizeM,
            ),
            showDivider: true,
            onTap: () async {
              UIUtils.showLoading(tips: '正在迁移文章封面属性，请稍等...');
              try {
                await controller.migrateArticleCoverImages();
                Get.close();
                UIUtils.showSuccess("文章封面属性迁移完成");
              } catch (e) {
                Get.close();
                UIUtils.showError("操作失败: $e");
              }
            },
          ),
          SimpleSettingsTile(
            title: '清空所有标签',
            subtitle: '删除所有已创建的标签',
            leading: Icon(
              Icons.clean_hands,
              color: AppTheme.getColorScheme(context).primary,
              size: Dimensions.iconSizeM,
            ),
            showDivider: false,
            onTap: () async {
              UIUtils.showLoading();
              TagRepository.removeAll();
              Get.offNamed(Routes.ARTICLES);
              UIUtils.showSuccess("清除标签完成");
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
        titleTextStyle: MyFontStyle.settingGroupTitleThemed(context),
        subtitle: '应用信息与更新',
        subtitleTextStyle: MyFontStyle.cardSubtitleStyleThemed(context),
        children: [
          SimpleSettingsTile(
            title: '检查更新',
            subtitle: '获取最新版本的应用',
            leading: Icon(Icons.system_update, color: AppColors.primary(context), size: Dimensions.iconSizeM),
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
        titleTextStyle: MyFontStyle.settingGroupTitleThemed(context),
        subtitle: '配置AI相关功能',
        subtitleTextStyle: MyFontStyle.cardSubtitleStyleThemed(context),
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
