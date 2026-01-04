/// Backup Settings Controller Provider
///
/// 备份设置控制器，管理备份配置。

// ignore_for_file: use_of_void_result

library;

import 'dart:io';

import 'package:daily_satori/app_exports.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:file_picker/file_picker.dart';
import 'package:permission_handler/permission_handler.dart';

part 'backup_settings_controller_provider.freezed.dart';
part 'backup_settings_controller_provider.g.dart';

/// BackupSettingsController 状态
@freezed
abstract class BackupSettingsControllerState with _$BackupSettingsControllerState {
  const factory BackupSettingsControllerState({
    @Default(false) bool isLoading,
    @Default('') String errorMessage,
    @Default('') String backupDirectory,
  }) = _BackupSettingsControllerState;
}

/// BackupSettingsController Provider
@riverpod
class BackupSettingsController extends _$BackupSettingsController {
  @override
  BackupSettingsControllerState build() {
    // 直接从仓储读取初始值，避免异步调用中访问未初始化的 state
    final path = SettingRepository.i.getSetting(SettingService.backupDirKey);
    return BackupSettingsControllerState(backupDirectory: path);
  }

  /// 重新加载备份设置
  Future<void> loadSettings() async {
    state = state.copyWith(isLoading: true, errorMessage: '');
    try {
      final path = SettingRepository.i.getSetting(SettingService.backupDirKey);
      state = state.copyWith(isLoading: false, backupDirectory: path);
    } catch (e) {
      logger.e('[BackupSettingsController] 加载设置失败', error: e);
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  /// 选择备份目录
  Future<void> selectBackupDirectory() async {
    try {
      // Android 平台需要检查存储权限
      if (Platform.isAndroid) {
        final permission = await Permission.manageExternalStorage.request();
        if (!permission.isGranted) {
          UIUtils.showError('请授予应用管理外部存储的权限', isTop: true);
          return;
        }
      }

      String? selectedDirectory = await FilePicker.platform.getDirectoryPath(
        dialogTitle: '选择备份文件夹',
        initialDirectory: state.backupDirectory,
      );

      if (selectedDirectory != null) {
        SettingRepository.i.saveSetting(SettingService.backupDirKey, selectedDirectory);
        state = state.copyWith(backupDirectory: selectedDirectory);
        UIUtils.showSuccess('backup_settings.path_saved'.t, isTop: true);
      }
    } catch (e) {
      logger.e('[BackupSettingsController] 选择目录失败', error: e);
      UIUtils.showError('backup_settings.select_failed'.t);
    }
  }

  /// 执行备份
  Future<void> performBackup() async {
    state = state.copyWith(isLoading: true);
    try {
      final success = await BackupService.i.checkAndBackup(immediateBackup: true);
      state = state.copyWith(isLoading: false);
      if (success) {
        UIUtils.showSuccess('backup_settings.backup_success'.t);
      } else {
        UIUtils.showError('backup_settings.backup_failed'.t);
      }
    } catch (e) {
      logger.e('[BackupSettingsController] 备份失败', error: e);
      state = state.copyWith(isLoading: false);
      UIUtils.showError('backup_settings.backup_failed'.t);
    }
  }
}