import 'dart:io';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/components/button_styles.dart';
// snackbar 样式通过 UIUtils 统一封装使用
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/global.dart';

import '../controllers/backup_restore_controller.dart';

class BackupRestoreView extends GetView<BackupRestoreController> {
  const BackupRestoreView({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('从备份恢复', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500)),
        centerTitle: true,
        elevation: 0,
      ),
      body: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
        child: Obx(() {
          return controller.backupList.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.backup_outlined, size: 64, color: Colors.grey),
                      const SizedBox(height: 16),
                      const Text("暂无备份信息", style: TextStyle(fontSize: 16, color: Colors.grey)),
                    ],
                  ),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 16.0),
                      child: Row(
                        children: [
                          const Icon(Icons.restore, size: 20),
                          const SizedBox(width: 8),
                          Text(
                            '可用备份文件（${controller.backupList.length}）',
                            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: ListView.separated(
                        itemCount: controller.backupList.length,
                        separatorBuilder: (context, index) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          return Obx(() {
                            bool isSelected = controller.selectedBackupIndex.value == index;
                            return Card(
                              elevation: isSelected ? 2 : 0,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                                side: BorderSide(
                                  color: isSelected ? Theme.of(context).primaryColor : Colors.transparent,
                                  width: 1.5,
                                ),
                              ),
                              margin: const EdgeInsets.symmetric(vertical: 4),
                              child: ListTile(
                                contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                                leading: Icon(
                                  Icons.history,
                                  color: isSelected ? Theme.of(context).primaryColor : Colors.grey,
                                ),
                                title: Text(
                                  '备份 ${index + 1}',
                                  style: TextStyle(
                                    fontSize: 15,
                                    fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
                                  ),
                                ),
                                subtitle: Text(
                                  '创建时间: ${controller.getBackupTime(controller.backupList[index])}',
                                  style: const TextStyle(fontSize: 13),
                                ),
                                trailing: isSelected
                                    ? Icon(Icons.check_circle, color: Theme.of(context).primaryColor)
                                    : null,
                                selected: isSelected,
                                selectedTileColor: Theme.of(context).primaryColor.withValues(alpha: 0.05),
                                onTap: () {
                                  controller.selectedBackupIndex.value = index;
                                },
                              ),
                            );
                          });
                        },
                      ),
                    ),
                    _buildRestoreButton(context),
                  ],
                );
        }),
      ),
    );
  }

  Widget _buildRestoreButton(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 20.0),
      child: Obx(
        () => ElevatedButton.icon(
          icon: const Icon(Icons.restore_rounded),
          label: const Text("还原选中的备份", style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
          style: ButtonStyles.getPrimaryStyle(context),
          onPressed: controller.selectedBackupIndex.value >= 0
              ? () async {
                  final result = await controller.restoreBackup();
                  if (result) {
                    UIUtils.showSuccess('备份文件已成功还原', title: '还原成功');
                  } else {
                    UIUtils.showError('备份文件不存在或已损坏', title: '还原失败');
                  }

                  if (AppInfoUtils.isProduction && result) {
                    Get.defaultDialog(
                      title: "重启应用",
                      titleStyle: const TextStyle(fontWeight: FontWeight.w600),
                      middleText: "需要重启应用以完成还原，点击确定重启应用",
                      contentPadding: const EdgeInsets.all(20),
                      confirmTextColor: Colors.white,
                      buttonColor: colorScheme.primary,
                      onConfirm: () {
                        exit(0);
                      },
                      onCancel: () {
                        Navigator.pop(Get.context!);
                      },
                    );
                  }
                }
              : null,
        ),
      ),
    );
  }
}
