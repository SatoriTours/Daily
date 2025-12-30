/// Backup Restore Controller Provider
///
/// 备份恢复控制器，管理备份列表和恢复操作。

library;

import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:path/path.dart' as p;

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/navigation/app_navigation.dart';

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
    _loadBackupPath();
    return const BackupRestoreControllerState();
  }

  /// 加载备份路径
  void _loadBackupPath() {
    try {
      final path = SettingRepository.i.getSetting(SettingService.backupDirKey);
      state = state.copyWith(backupPath: path);
      loadBackupFiles();
    } catch (e) {
      logger.e('[BackupRestoreController] 加载备份路径失败', error: e);
    }
  }

  /// 加载备份文件列表
  Future<void> loadBackupFiles() async {
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

      final entities = directory.listSync();
      final backupList = entities.where((entity) {
        return entity is Directory && p.basename(entity.path).startsWith('daily_satori_backup_');
      }).toList();

      // Sort by name descending (newest first)
      backupList.sort((a, b) => b.path.compareTo(a.path));

      state = state.copyWith(isLoading: false, backupList: backupList);
    } catch (e) {
      logger.e('[BackupRestoreController] 加载备份文件失败', error: e);
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  void selectBackupIndex(int index) {
    state = state.copyWith(selectedBackupIndex: index);
  }

  String getBackupTime(FileSystemEntity file) {
    final name = p.basename(file.path);
    // daily_satori_backup_2023-10-27-10-00-00
    const prefix = 'daily_satori_backup_';
    if (name.startsWith(prefix)) {
      final timestamp = name.substring(prefix.length);
      // Format: YYYY-MM-DD-HH-MM-SS
      // Convert to YYYY-MM-DD HH:MM:SS
      final parts = timestamp.split('-');
      if (parts.length >= 6) {
        return '${parts[0]}-${parts[1]}-${parts[2]} ${parts[3]}:${parts[4]}:${parts[5]}';
      }
      return timestamp;
    }
    return '';
  }

  /// 恢复备份
  Future<bool> restoreBackup() async {
    if (state.selectedBackupIndex < 0 || state.selectedBackupIndex >= state.backupList.length) {
      return false;
    }

    final file = state.backupList[state.selectedBackupIndex];
    final name = p.basename(file.path);
    const prefix = 'daily_satori_backup_';
    final backupName = name.startsWith(prefix) ? name.substring(prefix.length) : name;

    state = state.copyWith(isRestoring: true, errorMessage: '');
    try {
      final success = await BackupService.i.restoreBackup(backupName);
      state = state.copyWith(isRestoring: false);

      if (success) {
        // 重新加载数据
        ref.invalidate(articleStateProvider);
        ref.invalidate(diaryStateProvider);
        ref.invalidate(booksStateProvider);
        return true;
      } else {
        return false;
      }
    } catch (e) {
      logger.e('[BackupRestoreController] 恢复备份失败', error: e);
      state = state.copyWith(isRestoring: false, errorMessage: e.toString());
      return false;
    }
  }

  /// 删除备份文件
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
