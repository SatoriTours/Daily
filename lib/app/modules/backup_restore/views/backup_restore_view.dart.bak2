import 'dart:io';

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
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
        child: Obx(() => _buildBody(context)),
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (controller.backupList.isEmpty) {
      return const _EmptyBackupView();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _BackupHeader(count: controller.backupList.length),
        Expanded(
          child: _BackupList(
            itemCount: controller.backupList.length,
            selectedIndex: controller.selectedBackupIndex.value,
            createdAtOf: (i) => controller.getBackupTime(controller.backupList[i]),
            onTap: (i) => controller.selectedBackupIndex.value = i,
          ),
        ),
        _buildRestoreButton(context),
      ],
    );
  }

  Widget _buildRestoreButton(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 20.0),
      child: Obx(
        () => ElevatedButton.icon(
          icon: const Icon(Icons.restore_rounded),
          label: const Text("还原选中的备份", style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
          style: ButtonStyles.getPrimaryStyle(context),
          onPressed: controller.selectedBackupIndex.value >= 0 ? () => _onRestorePressed(context) : null,
        ),
      ),
    );
  }

  Future<void> _onRestorePressed(BuildContext context) async {
    final result = await controller.restoreBackup();
    if (result) {
      UIUtils.showSuccess('备份文件已成功还原', title: '还原成功');
    } else {
      UIUtils.showError('备份文件不存在或已损坏', title: '还原失败');
    }

    if (AppInfoUtils.isProduction && result) {
      final confirmed = await DialogUtils.showConfirm(
        title: '重启应用',
        message: '需要重启应用以完成还原，点击确定重启应用',
        confirmText: '确定',
        cancelText: '取消',
      );
      if (confirmed) {
        exit(0);
      }
    }
  }
}

/// 空状态视图
class _EmptyBackupView extends StatelessWidget {
  const _EmptyBackupView();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.backup_outlined, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('暂无备份信息', style: TextStyle(fontSize: 16, color: Colors.grey)),
        ],
      ),
    );
  }
}

/// 头部信息
class _BackupHeader extends StatelessWidget {
  final int count;
  const _BackupHeader({required this.count});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16.0),
      child: Row(
        children: [
          const Icon(Icons.restore, size: 20),
          const SizedBox(width: 8),
          Text('可用备份文件（$count）', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}

/// 备份项
class _BackupItem extends StatelessWidget {
  final int index;
  final String? createdAt;
  final bool isSelected;
  final VoidCallback onTap;

  const _BackupItem({required this.index, required this.createdAt, required this.isSelected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).primaryColor;
    return Card(
      elevation: isSelected ? 2 : 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: isSelected ? primary : Colors.transparent, width: 1.5),
      ),
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: Icon(Icons.history, color: isSelected ? primary : Colors.grey),
        title: Text(
          '备份 ${index + 1}',
          style: TextStyle(fontSize: 15, fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal),
        ),
        subtitle: Text('创建时间: ${createdAt ?? '-'}', style: const TextStyle(fontSize: 13)),
        trailing: isSelected ? Icon(Icons.check_circle, color: primary) : null,
        selected: isSelected,
        selectedTileColor: primary.withValues(alpha: 0.05),
        onTap: onTap,
      ),
    );
  }
}

/// 备份列表
class _BackupList extends StatelessWidget {
  final int itemCount;
  final int selectedIndex;
  final String? Function(int) createdAtOf;
  final void Function(int) onTap;

  const _BackupList({
    required this.itemCount,
    required this.selectedIndex,
    required this.createdAtOf,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListView.separated(
      itemCount: itemCount,
      separatorBuilder: (context, index) => const Divider(height: 1),
      itemBuilder: (context, index) => _BackupItem(
        index: index,
        createdAt: createdAtOf(index),
        isSelected: selectedIndex == index,
        onTap: () => onTap(index),
      ),
    );
  }
}
