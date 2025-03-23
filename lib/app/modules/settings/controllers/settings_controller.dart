import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/global.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/migration_service.dart';
import 'package:permission_handler/permission_handler.dart';

class SettingsController extends GetxController {
  final webServiceAddress = ''.obs;
  final webAccessUrl = ''.obs;

  @override
  void onInit() {
    super.onInit();
    _updateWebServerAddress();
    _updateWebAccessUrl();
  }

  void _updateWebServerAddress() async {
    webServiceAddress.value = await WebService.i.getAppAddress();
  }

  void _updateWebAccessUrl() {
    webAccessUrl.value = WebService.i.webSocketTunnel.getWebAccessUrl();
  }

  /// 选择备份目录
  void selectBackupDirectory() async {
    // 检查是否有权限
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();
    if (!manageExternalStoragePermission.isGranted) {
      errorNotice('请授予应用管理外部存储的权限');
      return;
    }

    final String? selectedDirectory = await FilePicker.platform.getDirectoryPath(
      dialogTitle: '选择备份文件夹',
      initialDirectory: SettingRepository.getSetting(SettingService.backupDirKey),
    );

    if (selectedDirectory != null) {
      final backupDir = selectedDirectory;
      logger.i('选择备份目录: $backupDir');
      SettingRepository.saveSetting(SettingService.backupDirKey, backupDir);
    }
  }

  /// 迁移文章图片，只保留封面图
  Future<void> migrateArticleImages() async {
    try {
      await MigrationService.i.migrateArticleImages();
      logger.i('文章图片迁移成功');
      return;
    } catch (e) {
      logger.e('文章图片迁移失败: $e');
      throw e;
    }
  }

  /// 迁移文章封面属性
  Future<void> migrateArticleCoverImages() async {
    try {
      await MigrationService.i.migrateArticleCoverImages();
      logger.i('文章封面属性迁移成功');
      return;
    } catch (e) {
      logger.e('文章封面属性迁移失败: $e');
      throw e;
    }
  }

  void copyWebServiceAddress() {
    Clipboard.setData(ClipboardData(text: webServiceAddress.value));
  }

  void copyWebAccessUrl() {
    Clipboard.setData(ClipboardData(text: webAccessUrl.value));
  }
}
