import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/pages/backup_restore/providers/backup_restore_controller_provider.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/providers/first_launch_provider.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';

/// 备份恢复页面
class BackupRestoreView extends ConsumerWidget {
  const BackupRestoreView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(backupRestoreControllerProvider);
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: const SAppBar(
        title: Text('从备份恢复', style: TextStyle(color: Colors.white)),
        centerTitle: true,
        elevation: 0,
        backgroundColorLight: AppColors.primary,
        backgroundColorDark: AppColors.backgroundDark,
        foregroundColor: Colors.white,
      ),
      body: Padding(padding: Dimensions.paddingPage, child: _buildBody(context, ref, state)),
    );
  }

  Widget _buildBody(BuildContext context, WidgetRef ref, BackupRestoreControllerState state) {
    if (state.errorMessage.isNotEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: Dimensions.iconSizeXxl, color: AppColors.getError(context)),
            Dimensions.verticalSpacerM,
            Text(
              state.errorMessage,
              style: AppTypography.bodyMedium.copyWith(color: AppColors.getError(context)),
              textAlign: TextAlign.center,
            ),
            Dimensions.verticalSpacerL,
            ElevatedButton(
              onPressed: () => ref.read(backupRestoreControllerProvider.notifier).loadBackupFiles(),
              child: Text('button.refresh'.t),
            ),
          ],
        ),
      );
    }

    if (state.backupList.isEmpty) {
      return const _EmptyBackupView();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _BackupHeader(count: state.backupList.length),
        Dimensions.verticalSpacerS,
        Expanded(
          child: _BackupList(
            itemCount: state.backupList.length,
            selectedIndex: state.selectedBackupIndex,
            createdAtOf: (i) => ref.read(backupRestoreControllerProvider.notifier).getBackupTime(state.backupList[i]),
            onTap: (i) => ref.read(backupRestoreControllerProvider.notifier).selectBackupIndex(i),
          ),
        ),
        _buildRestoreButton(context, ref, state),
      ],
    );
  }

  Widget _buildRestoreButton(BuildContext context, WidgetRef ref, BackupRestoreControllerState state) {
    return Container(
      width: double.infinity,
      padding: Dimensions.paddingVerticalM,
      child: ElevatedButton.icon(
        icon: const Icon(Icons.restore_rounded, size: Dimensions.iconSizeM + 2),
        label: Text("还原选中的备份", style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.w600)),
        style: ButtonStyles.getPrimaryStyle(context).copyWith(
          padding: const WidgetStatePropertyAll(
            EdgeInsets.symmetric(horizontal: Dimensions.spacingXl, vertical: Dimensions.spacingM),
          ),
        ),
        onPressed: state.selectedBackupIndex >= 0 ? () => _onRestorePressed(context, ref) : null,
      ),
    );
  }

  Future<void> _onRestorePressed(BuildContext context, WidgetRef ref) async {
    final result = await ref.read(backupRestoreControllerProvider.notifier).restoreBackup();
    if (result) {
      // 标记设置完成，备份恢复后不需要再显示引导
      ref.read(firstLaunchControllerProvider.notifier).markSetupComplete();

      // 显示成功提示对话框
      if (context.mounted) {
        await showDialog(
          context: context,
          barrierDismissible: false,
          builder: (context) => AlertDialog(
            icon: Icon(Icons.check_circle_rounded, color: AppColors.getSuccess(context), size: Dimensions.iconSizeXxl),
            title: const Text('备份恢复完成'),
            content: const Text('您的数据已成功恢复！\n\n为了确保所有数据正常加载，应用将会退出。请重新打开应用以查看恢复的内容。', style: TextStyle(height: 1.5)),
            actions: [
              FilledButton.icon(
                icon: const Icon(Icons.exit_to_app_rounded, size: Dimensions.iconSizeM),
                label: const Text('确定并退出'),
                style: ButtonStyles.getPrimaryStyle(context),
                onPressed: () {
                  AppNavigation.back();
                  // 延迟一下再退出，让对话框有时间关闭
                  Future.delayed(const Duration(milliseconds: 300), () {
                    AppNavigation.exitApp();
                  });
                },
              ),
            ],
            actionsAlignment: MainAxisAlignment.center,
          ),
        );
      }
    } else {
      UIUtils.showError('备份文件不存在或已损坏', title: '还原失败');
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
              color: colorScheme.surfaceContainerHighest.withValues(alpha: Opacities.half),
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.backup_outlined,
              size: Dimensions.iconSizeXxl,
              color: colorScheme.onSurface.withValues(alpha: Opacities.high),
            ),
          ),
          Dimensions.verticalSpacerL,
          Text(
            '暂无备份信息',
            style: textTheme.titleMedium?.copyWith(color: colorScheme.onSurface.withValues(alpha: Opacities.half)),
          ),
          Dimensions.verticalSpacerS,
          Text(
            '请先在备份设置中创建备份',
            style: textTheme.bodySmall?.copyWith(color: colorScheme.onSurface.withValues(alpha: Opacities.higher)),
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

    return Row(
      children: [
        Icon(Icons.folder_open_rounded, size: Dimensions.iconSizeM, color: colorScheme.primary),
        Dimensions.horizontalSpacerS,
        Text('找到 $count 个备份文件', style: AppTypography.bodyMedium.copyWith(color: colorScheme.onSurfaceVariant)),
      ],
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
    final primary = colorScheme.primary;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        side: BorderSide(color: isSelected ? primary : colorScheme.outlineVariant, width: isSelected ? 2 : 1),
      ),
      margin: const EdgeInsets.only(bottom: Dimensions.spacingS),
      color: isSelected ? primary.withValues(alpha: Opacities.extraLow) : colorScheme.surface,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
          child: Row(
            children: [
              // 选中图标
              Icon(
                isSelected ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
                color: isSelected ? primary : colorScheme.outline,
                size: Dimensions.iconSizeL,
              ),
              Dimensions.horizontalSpacerM,
              // 文本信息
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '备份 ${index + 1}',
                      style: AppTypography.titleSmall.copyWith(
                        fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                        color: isSelected ? primary : colorScheme.onSurface,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      createdAt ?? '-',
                      style: AppTypography.bodySmall.copyWith(color: colorScheme.onSurfaceVariant),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
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
      padding: EdgeInsets.zero,
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
