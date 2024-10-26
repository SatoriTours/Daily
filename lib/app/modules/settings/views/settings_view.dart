import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/settings_service.dart';
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
      ],
    );
  }

  Widget _buildOpenAIGroup() {
    return SettingsGroup(
      title: 'OpenAI',
      titleTextStyle: MyFontStyle.settingGroupTitle,
      children: [
        TextInputSettingsTile(
          settingKey: SettingsService.openAIAddressKey,
          title: 'OpenAI 地址',
          helperText: '例如: https://api.openai.com/v1/',
        ),
        TextInputSettingsTile(
          settingKey: SettingsService.openAITokenKey,
          title: 'OpenAI token',
          helperText: '输入OpenAI token',
        ),
      ],
    );
  }
}
