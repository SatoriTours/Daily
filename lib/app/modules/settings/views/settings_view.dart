import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import '../controllers/settings_controller.dart';

class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    controller.initData();
    return Scaffold(
      appBar: AppBar(
        title: const Text('配置页'),
        centerTitle: true,
      ),
      body: GestureDetector(
        onTap: () {
          FocusScope.of(context).unfocus(); // 点击空白处去掉输入框焦点
        },
        child: Center(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: 32.0, vertical: 16.0), // 增加输入框间距
            child: Column(
              mainAxisAlignment: MainAxisAlignment.start,
              children: [
                _buildOpenAIUrlField(),
                SizedBox(height: 16.0), // 增加输入框之间的间距
                _buildOpenAITokenField(),
                SizedBox(height: 16.0),
                _buildBackupDirSelect(),
                _buildBackupAction(),
                _buildBackupAndRestoreActions(),
                _buildFreeSpaceAction(),
                Spacer(), // 添加间隔以将按钮推到页面底部
                _buildSaveButton(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBackupDirSelect() {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: controller.backupDir,
            decoration: InputDecoration(
              labelText: '备份路径',
            ),
            readOnly: true, // 设置为只读，防止用户手动输入
          ),
        ),
      ],
    );
  }

  Widget _buildBackupAction() {
    return Row(
      children: [
        Spacer(),
        TextButton.icon(
          icon: Icon(Icons.folder_open),
          label: Text("选择备份路径"),
          onPressed: () async {
            controller.selectBackupDirectory();
          },
        ),
      ],
    );
  }

  Widget _buildBackupAndRestoreActions() {
    return Row(
      children: [
        TextButton.icon(
          icon: Icon(Icons.backup_table_outlined),
          label: Text("立即备份"),
          onPressed: () async {
            await BackupService.i.checkAndBackup(immediateBackup: true);
            successNotice("备份成功");
          },
        ),
        TextButton.icon(
          icon: Icon(Icons.restore_rounded),
          label: Text("备份恢复"),
          onPressed: () async {
            Get.toNamed(Routes.BACKUP_RESTORE);
          },
        ),
      ],
    );
  }

  Widget _buildFreeSpaceAction() {
    return Row(
      children: [
        TextButton.icon(
          icon: Icon(Icons.cleaning_services),
          label: Text("清除空间"),
          onPressed: () async {
            await FreeDiskService.i.clean();
            successNotice("清除空间成功");
          },
        ),
        Spacer(),
      ],
    );
  }

  Widget _buildOpenAIUrlField() {
    return TextField(
      controller: controller.openaiAddress,
      decoration: InputDecoration(
        labelText: 'openai 地址',
      ),
    );
  }

  Widget _buildOpenAITokenField() {
    return TextField(
      controller: controller.openaiToken,
      decoration: InputDecoration(
        labelText: 'openai token',
      ),
    );
  }

  Widget _buildSaveButton(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        SizedBox(
          width: MediaQuery.of(context).size.width / 2, // 设置宽度为页面的一半
          child: TextButton(
            style: TextButton.styleFrom(
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
            ),
            child: const Text("保存"),
            onPressed: () async {
              await controller.save();
              successNotice('设置已保存');
            },
          ),
        ),
      ],
    );
  }
}
