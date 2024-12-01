import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter_settings_screens/flutter_settings_screens.dart';

import 'package:get/get.dart';

import '../controllers/settings_controller.dart';

class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('配置页'),
        titleTextStyle: MyFontStyle.appBarTitleStyle,
        centerTitle: true,
      ),
      body: Padding(
        padding: EdgeInsets.only(left: 5),
        child: SingleChildScrollView(
          child: Column(
            children: [
              _buildOpenAIGroup(),
              _buildBackupDirSelect(),
              _buildFreeSpaceAction(),
              _buildUpgradeAction(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBackupDirSelect() {
    return SettingsGroup(
      title: '备份',
      titleTextStyle: MyFontStyle.settingGroupTitle,
      children: [
        SimpleSettingsTile(
          title: '选择备份路径',
          leading: Icon(Icons.folder_open_outlined),
          showDivider: false,
          onTap: () {
            controller.selectBackupDirectory();
          },
        ),
        SimpleSettingsTile(
          title: '立即备份',
          leading: Icon(Icons.backup_table_outlined),
          showDivider: false,
          onTap: () async {
            showFullScreenLoading();
            await BackupService.i.checkAndBackup(immediateBackup: true);
            Get.close();
            successNotice("备份成功");
          },
        ),
        SimpleSettingsTile(
          title: '从备份恢复',
          leading: Icon(Icons.restore_rounded),
          showDivider: false,
          onTap: () {
            Get.toNamed(Routes.BACKUP_RESTORE);
          },
        ),
      ],
    );
  }

  Widget _buildFreeSpaceAction() {
    return SettingsGroup(
      title: '清理应用',
      titleTextStyle: MyFontStyle.settingGroupTitle,
      children: [
        SimpleSettingsTile(
          title: '清除重复的图片',
          leading: Icon(Icons.cleaning_services),
          showDivider: false,
          onTap: () async {
            showFullScreenLoading();
            await FreeDiskService.i.clean();
            Get.close();
            successNotice("清除重复图片完成");
          },
        ),
        SimpleSettingsTile(
          title: '清空所有标签',
          leading: Icon(Icons.clean_hands),
          showDivider: false,
          onTap: () async {
            showFullScreenLoading();
            await TagsService.i.clearAllTags();
            Get.offNamed(Routes.ARTICLES);
            successNotice("清除标签完成");
          },
        ),
      ],
    );
  }

  Widget _buildUpgradeAction() {
    return SettingsGroup(
      title: '关于应用',
      titleTextStyle: MyFontStyle.settingGroupTitle,
      children: [
        SimpleSettingsTile(
          title: '更新应用',
          leading: Icon(Icons.download),
          showDivider: false,
          onTap: () {
            AppUpgradeService.i.checkAndDownload();
          },
        ),
      ],
    );
  }

  Widget _buildOpenAIGroup() {
    return SettingsGroup(
      title: 'OpenAI',
      titleTextStyle: MyFontStyle.settingGroupTitle,
      children: [
        TextInputSettingsTile(
          settingKey: SettingService.openAIAddressKey,
          title: 'OpenAI 地址',
          helperText: '例如: https://api.openai.com/v1/',
        ),
        TextInputSettingsTile(
          settingKey: SettingService.openAITokenKey,
          title: 'OpenAI token',
          helperText: '输入OpenAI token',
        ),
      ],
    );
  }
}
