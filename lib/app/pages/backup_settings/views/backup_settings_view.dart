import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';

class BackupSettingsView extends ConsumerWidget {
  const BackupSettingsView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(backupSettingsControllerProvider);
    final colorScheme = AppTheme.getColorScheme(context);

    return Scaffold(
      backgroundColor: colorScheme.surface,
      appBar: const SAppBar(
        title: Text('备份与恢复', style: TextStyle(color: Colors.white)),
        centerTitle: true,
        elevation: 0,
        backgroundColorLight: AppColors.primary,
        backgroundColorDark: AppColors.backgroundDark,
        foregroundColor: Colors.white,
      ),
      body: _buildBody(context, ref, state),
    );
  }

  Widget _buildBody(BuildContext context, WidgetRef ref, BackupSettingsControllerState state) {
    final hasBackupDirectory = state.backupDirectory.isNotEmpty;

    if (!hasBackupDirectory) {
      return _buildSelectDirectoryPrompt(context, ref);
    }

    return _buildMainContent(context, ref, state);
  }

  Widget _buildSelectDirectoryPrompt(BuildContext context, WidgetRef ref) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Center(
      child: Padding(
        padding: Dimensions.paddingPage,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                color: colorScheme.primaryContainer.withValues(alpha: Opacities.high),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.folder_open_rounded, size: 64, color: colorScheme.primary),
            ),
            Dimensions.verticalSpacerL,
            Dimensions.verticalSpacerL,
            Text('请选择备份目录', style: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
            Dimensions.verticalSpacerM,
            Text(
              '选择一个文件夹存储您的应用数据备份',
              style: textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurface.withValues(alpha: Opacities.higherOpaque),
              ),
              textAlign: TextAlign.center,
            ),
            Dimensions.verticalSpacerL,
            Dimensions.verticalSpacerL,
            ElevatedButton.icon(
              onPressed: () => ref.read(backupSettingsControllerProvider.notifier).selectBackupDirectory(),
              icon: const Icon(Icons.create_new_folder_rounded, size: Dimensions.iconSizeM),
              label: const Text('选择备份目录'),
              style: ButtonStyles.getPrimaryStyle(context).copyWith(
                padding: const WidgetStatePropertyAll(
                  EdgeInsets.symmetric(horizontal: Dimensions.spacingXl, vertical: Dimensions.spacingM),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMainContent(BuildContext context, WidgetRef ref, BackupSettingsControllerState state) {
    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: Dimensions.paddingPage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSectionTitle(context, '备份目录', Icons.folder_rounded),
          Dimensions.verticalSpacerM,
          _buildDirectoryCard(context, ref, state),
          Dimensions.verticalSpacerL,
          Dimensions.verticalSpacerL,
          _buildSectionTitle(context, '操作', Icons.touch_app_rounded),
          Dimensions.verticalSpacerM,
          _buildActionButton(
            context,
            title: '立即备份',
            subtitle: '保存当前所有应用数据',
            icon: Icons.backup_rounded,
            color: AppTheme.getColorScheme(context).primary,
            onTap: () => _onBackupPressed(context, ref),
          ),
          Dimensions.verticalSpacerM,
          _buildActionButton(
            context,
            title: '恢复备份',
            subtitle: '从备份文件中恢复数据',
            icon: Icons.restore_rounded,
            color: Colors.deepPurple,
            onTap: () => AppNavigation.toNamed(Routes.backupRestore),
          ),
          Dimensions.verticalSpacerL,
          Dimensions.verticalSpacerL,
          _buildTipCard(context),
        ],
      ),
    );
  }

  Widget _buildDirectoryCard(BuildContext context, WidgetRef ref, BackupSettingsControllerState state) {
    final textTheme = AppTheme.getTextTheme(context);
    const cardColor = Colors.teal;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingL, vertical: Dimensions.spacingS),
      decoration: BoxDecoration(
        color: cardColor.withValues(alpha: Opacities.low),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: cardColor.withValues(alpha: Opacities.mediumHigh), width: 1.5),
      ),
      child: Row(
        children: [
          Container(
            width: Dimensions.iconSizeXxl,
            height: Dimensions.iconSizeXxl,
            decoration: BoxDecoration(
              color: cardColor.withValues(alpha: Opacities.mediumLow),
              borderRadius: BorderRadius.circular(Dimensions.radiusS),
            ),
            child: Icon(Icons.folder_rounded, size: Dimensions.iconSizeXl - 6, color: cardColor),
          ),
          Dimensions.horizontalSpacerM,
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '备份位置',
                  style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: cardColor),
                ),
                Dimensions.verticalSpacerXs,
                Text(
                  state.backupDirectory,
                  style: textTheme.bodySmall?.copyWith(color: cardColor.withValues(alpha: Opacities.highOpaque)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          Dimensions.horizontalSpacerS,
          InkWell(
            onTap: () => ref.read(backupSettingsControllerProvider.notifier).selectBackupDirectory(),
            borderRadius: BorderRadius.circular(Dimensions.radiusS),
            child: Container(
              padding: Dimensions.paddingS,
              decoration: BoxDecoration(
                color: cardColor.withValues(alpha: Opacities.mediumLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
              ),
              child: Icon(Icons.edit_outlined, size: Dimensions.iconSizeM, color: cardColor),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionButton(
    BuildContext context, {
    required String title,
    String? subtitle,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    final textTheme = AppTheme.getTextTheme(context);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(Dimensions.radiusM),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(Dimensions.spacingM + 2),
        decoration: BoxDecoration(
          color: color.withValues(alpha: Opacities.low),
          borderRadius: BorderRadius.circular(Dimensions.radiusM),
          border: Border.all(color: color.withValues(alpha: Opacities.mediumHigh), width: 1.5),
        ),
        child: Row(
          children: [
            Container(
              width: Dimensions.iconSizeXxl,
              height: Dimensions.iconSizeXxl,
              decoration: BoxDecoration(
                color: color.withValues(alpha: Opacities.mediumLow),
                borderRadius: BorderRadius.circular(Dimensions.radiusS),
              ),
              child: Icon(icon, size: Dimensions.iconSizeXl - 6, color: color),
            ),
            Dimensions.horizontalSpacerM,
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: color),
                  ),
                  if (subtitle != null) ...[
                    Dimensions.verticalSpacerXs,
                    Text(
                      subtitle,
                      style: textTheme.bodySmall?.copyWith(color: color.withValues(alpha: Opacities.highOpaque)),
                    ),
                  ],
                ],
              ),
            ),
            Icon(
              Icons.chevron_right_rounded,
              size: Dimensions.iconSizeM + 2,
              color: color.withValues(alpha: Opacities.higherOpaque),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(BuildContext context, String title, IconData icon) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Row(
      children: [
        Icon(icon, size: Dimensions.iconSizeM, color: colorScheme.primary),
        Dimensions.horizontalSpacerS,
        Text(
          title,
          style: textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w600,
            color: colorScheme.onSurface.withValues(alpha: Opacities.veryLowOpaque),
          ),
        ),
      ],
    );
  }

  Widget _buildTipCard(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      padding: Dimensions.paddingM,
      decoration: BoxDecoration(
        color: colorScheme.primary.withValues(alpha: Opacities.low),
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        border: Border.all(color: colorScheme.primary.withValues(alpha: Opacities.medium), width: 1),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.lightbulb_outline_rounded, size: Dimensions.iconSizeM, color: colorScheme.primary),
              Dimensions.horizontalSpacerS,
              Text(
                '备份说明',
                style: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.primary),
              ),
            ],
          ),
          Dimensions.verticalSpacerS,
          Text(
            '• 备份将保存您的全部应用数据，包括文章、笔记和设置\n'
            '• 备份文件存储在您选择的目录中\n'
            '• 建议定期备份数据以防丢失',
            style: textTheme.bodySmall?.copyWith(
              height: 1.6,
              color: colorScheme.onSurface.withValues(alpha: Opacities.highOpaque),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _onBackupPressed(BuildContext context, WidgetRef ref) async {
    await ref.read(backupSettingsControllerProvider.notifier).performBackup();
  }
}
