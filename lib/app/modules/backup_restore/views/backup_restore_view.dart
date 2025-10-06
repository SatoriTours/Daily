import 'dart:io';

import 'package:daily_satori/app/styles/index.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/global.dart';

import '../controllers/backup_restore_controller.dart';

class BackupRestoreView extends GetView<BackupRestoreController> {
  const BackupRestoreView({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: AppBar(
        title: Text('从备份恢复', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
        centerTitle: true,
        elevation: 0,
        backgroundColor: colorScheme.surface,
      ),
      body: Padding(padding: Dimensions.paddingPage, child: Obx(() => _buildBody(context))),
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
        Dimensions.verticalSpacerM,
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
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Obx(
        () => ElevatedButton.icon(
          icon: const Icon(Icons.restore_rounded, size: 22),
          label: Text("还原选中的备份", style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.w600)),
          style: ButtonStyles.getPrimaryStyle(
            context,
          ).copyWith(padding: const WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 32, vertical: 16))),
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
      await DialogUtils.showConfirm(
        title: '重启应用',
        message: '需要重启应用以完成还原，点击确定重启应用',
        confirmText: '确定',
        cancelText: '取消',
        onConfirm: () {
          exit(0);
        },
      );
    }
  }
}

/// 空状态视图
class _EmptyBackupView extends StatelessWidget {
  const _EmptyBackupView();

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 100,
            height: 100,
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.backup_outlined, size: 48, color: colorScheme.onSurface.withValues(alpha: 0.3)),
          ),
          Dimensions.verticalSpacerL,
          Text('暂无备份信息', style: textTheme.titleMedium?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.5))),
          Dimensions.verticalSpacerS,
          Text(
            '请先在备份设置中创建备份',
            style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.4)),
          ),
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
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.primaryContainer.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: colorScheme.primary.withValues(alpha: 0.2), width: 1),
      ),
      child: Row(
        children: [
          Icon(Icons.history_rounded, size: 22, color: colorScheme.primary),
          Dimensions.horizontalSpacerM,
          Text(
            '找到 $count 个备份文件',
            style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.primary),
          ),
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
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    final primary = colorScheme.primary;

    return Card(
      elevation: isSelected ? 2 : 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        side: BorderSide(
          color: isSelected ? primary : colorScheme.outline.withValues(alpha: 0.2),
          width: isSelected ? 2 : 1,
        ),
      ),
      margin: const EdgeInsets.symmetric(vertical: 6),
      color: isSelected ? primary.withValues(alpha: 0.08) : colorScheme.surface,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              // 图标
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: isSelected
                      ? primary.withValues(alpha: 0.15)
                      : colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(Dimensions.radiusS),
                ),
                child: Icon(
                  Icons.backup_rounded,
                  color: isSelected ? primary : colorScheme.onSurface.withValues(alpha: 0.5),
                  size: 24,
                ),
              ),
              Dimensions.horizontalSpacerM,
              // 文本信息
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '备份 ${index + 1}',
                      style: textTheme.titleSmall?.copyWith(
                        fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                        color: isSelected ? primary : colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Icon(Icons.access_time_rounded, size: 14, color: colorScheme.onSurface.withValues(alpha: 0.5)),
                        const SizedBox(width: 4),
                        Text(
                          createdAt ?? '-',
                          style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: 0.6)),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              // 选中图标
              if (isSelected)
                Icon(Icons.check_circle_rounded, color: primary, size: 28)
              else
                Icon(Icons.circle_outlined, color: colorScheme.onSurface.withValues(alpha: 0.3), size: 28),
            ],
          ),
        ),
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
    return ListView.builder(
      itemCount: itemCount,
      padding: const EdgeInsets.only(top: 8),
      physics: const BouncingScrollPhysics(),
      itemBuilder: (context, index) => _BackupItem(
        index: index,
        createdAt: createdAtOf(index),
        isSelected: selectedIndex == index,
        onTap: () => onTap(index),
      ),
    );
  }
}
