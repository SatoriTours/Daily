/// Backup Restore Controller Provider
///
/// 备份恢复控制器，管理备份列表和恢复操作。

library;

import 'dart:io';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:path/path.dart' as p;
import 'package:permission_handler/permission_handler.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/utils/utils.dart';

part 'backup_restore_controller_provider.freezed.dart';
part 'backup_restore_controller_provider.g.dart';

/// BackupRestoreController 状态
@freezed
abstract class BackupRestoreControllerState with _$BackupRestoreControllerState {
  const factory BackupRestoreControllerState({
    @Default(false) bool isLoading,
    @Default(false) bool isRestoring,
    @Default([]) List<FileSystemEntity> backupList,
    @Default(-1) int selectedBackupIndex,
    @Default('') String errorMessage,
    @Default('') String backupPath,
  }) = _BackupRestoreControllerState;
}

/// BackupRestoreController Provider
@riverpod
class BackupRestoreController extends _$BackupRestoreController {
  @override
  BackupRestoreControllerState build() {
    final path = SettingRepository.i.getSetting(SettingService.backupDirKey);
    logger.i('[BackupRestoreController] 读取到备份路径: "$path"');
    Future.microtask(() => loadBackupFiles());
    return BackupRestoreControllerState(backupPath: path);
  }

  Future<void> loadBackupFiles() async {
    logger.i('[BackupRestoreController] 开始加载备份文件');
    state = state.copyWith(isLoading: true, errorMessage: '');

    try {
      final path = state.backupPath;
      if (path.isEmpty) {
        state = state.copyWith(isLoading: false, backupList: [], errorMessage: 'backup_restore.no_backup_path'.t);
        return;
      }

      final directory = Directory(path);
      if (!await directory.exists()) {
        state = state.copyWith(isLoading: false, backupList: [], errorMessage: 'backup_restore.path_not_exist'.t);
        return;
      }

      final backupList =
          directory
              .listSync()
              .where((e) => e is Directory && p.basename(e.path).startsWith('daily_satori_backup_'))
              .toList()
            ..sort((a, b) => b.path.compareTo(a.path));

      logger.i('[BackupRestoreController] 找到备份数量: ${backupList.length}');
      state = state.copyWith(isLoading: false, backupList: backupList);
    } catch (e) {
      logger.e('[BackupRestoreController] 加载备份文件失败', error: e);
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  void selectBackupIndex(int index) => state = state.copyWith(selectedBackupIndex: index);

  String getBackupTime(FileSystemEntity file) {
    final name = p.basename(file.path);
    const prefix = 'daily_satori_backup_';
    if (!name.startsWith(prefix)) return '';

    final timestamp = name.substring(prefix.length);
    try {
      // 尝试解析 ISO 8601 格式
      final isoString = timestamp.replaceAllMapped(
        RegExp(r'(\d{4})-(\d{2})-(\d{2})T(\d{2})-(\d{2})-(\d{2})'),
        (m) => '${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}',
      );
      final dateTime = DateTime.tryParse(isoString);
      if (dateTime != null) return DateTimeUtils.formatDateTimeToLocal(dateTime);

      // 回退：尝试解析旧格式
      final parts = timestamp.split('-');
      if (parts.length >= 6) {
        final dt = DateTime.tryParse('${parts[0]}-${parts[1]}-${parts[2]} ${parts[3]}:${parts[4]}:${parts[5]}');
        if (dt != null) return DateTimeUtils.formatDateTimeToLocal(dt);
      }
    } catch (_) {}
    return timestamp;
  }

  Future<bool> restoreBackup() async {
    if (state.selectedBackupIndex < 0 || state.selectedBackupIndex >= state.backupList.length) return false;

    // 检查Android权限
    if (Platform.isAndroid) {
      final permission = await Permission.manageExternalStorage.request();
      if (!permission.isGranted) {
        UIUtils.showError('请授予应用管理外部存储的权限', isTop: true);
        return false;
      }
    }

    // 获取备份名称
    final backupName = _extractBackupName(state.backupList[state.selectedBackupIndex]);

    // 显示loading对话框
    DialogUtils.showLoading();
    state = state.copyWith(isRestoring: true, errorMessage: '');

    try {
      final success = await BackupService.i.restoreBackup(backupName);

      if (success) {
        // 刷新各模块状态
        ref.invalidate(articleStateProvider);
        ref.invalidate(diaryStateProvider);
        ref.invalidate(booksStateProvider);
      }

      return success;
    } catch (e) {
      logger.e('[BackupRestoreController] 恢复备份失败', error: e);
      state = state.copyWith(errorMessage: e.toString());
      return false;
    } finally {
      state = state.copyWith(isRestoring: false);
      DialogUtils.hideLoading();
    }
  }

  /// 从文件路径提取备份名称
  String _extractBackupName(FileSystemEntity file) {
    final name = p.basename(file.path);
    const prefix = 'daily_satori_backup_';
    return name.startsWith(prefix) ? name.substring(prefix.length) : name;
  }

  Future<void> deleteBackup(FileSystemEntity file) async {
    try {
      await file.delete(recursive: true);
      await loadBackupFiles();
      UIUtils.showSuccess('backup_restore.delete_success'.t);
    } catch (e) {
      logger.e('[BackupRestoreController] 删除备份失败', error: e);
      UIUtils.showError('backup_restore.delete_failed'.t);
    }
  }
}
