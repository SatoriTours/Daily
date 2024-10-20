import 'dart:io';

import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import '../controllers/backup_restore_controller.dart';

class BackupRestoreView extends GetView<BackupRestoreController> {
  const BackupRestoreView({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('从备份恢复'),
        centerTitle: true,
      ),
      body: Obx(() {
        return controller.backupList.isEmpty
            ? const Center(child: Text("暂无备份信息")) // 无数据时的提示
            : Column(
                children: [
                  Expanded(
                    child: ListView.builder(
                      itemCount: controller.backupList.length,
                      itemBuilder: (context, index) {
                        return Obx(() {
                          bool isSelected = controller.selectedBackupIndex.value == index; // 判断是否被选中
                          return ListTile(
                            title: Center(
                                child: Text(
                                    '${index + 1}. 备份时间: ${controller.getBackupTime(controller.backupList[index])}')),
                            tileColor: isSelected ? Colors.blue : null, // 设置选中背景色为蓝色
                            onTap: () {
                              controller.selectedBackupIndex.value = index; // 设置选中的索引
                            },
                          );
                        });
                      },
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: _buildRestoreButton(context),
                  ),
                ],
              );
      }),
    );
  }

  Widget _buildRestoreButton(BuildContext context) {
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
            child: const Text("还原"),
            onPressed: () async {
              await controller.restoreBackup();
              Get.snackbar('提示', '文件已还原', snackPosition: SnackPosition.top, backgroundColor: Colors.green);
              Get.defaultDialog(
                title: "重启应用",
                middleText: "点击确定, 重启应用",
                onConfirm: () {
                  // 关闭应用并重新启动
                  // exit(0);
                },
                onCancel: () {
                  // 关闭对话框
                  Navigator.pop(Get.context!);
                },
              );
            },
          ),
        ),
      ],
    );
  }
}
