import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/global.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:daily_satori/app/utils/ui_utils.dart';

class SettingsController extends GetxController {
  final webServiceAddress = ''.obs;
  final webAccessUrl = ''.obs;
  final isLoading = true.obs;
  final appVersion = ''.obs;

  @override
  void onInit() {
    super.onInit();
    _updateWebServerAddress();
    _updateWebAccessUrl();
    _loadAppVersion();
  }

  Future<void> _loadAppVersion() async {
    isLoading.value = true;
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      appVersion.value = '${packageInfo.version}+${packageInfo.buildNumber}';
    } catch (e) {
      logger.e('加载应用版本信息失败: $e');
      appVersion.value = '未知版本';
    } finally {
      isLoading.value = false;
    }
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
      UIUtils.showError('请授予应用管理外部存储的权限');
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

  void copyWebServiceAddress() {
    Clipboard.setData(ClipboardData(text: webServiceAddress.value));
  }

  void copyWebAccessUrl() {
    Clipboard.setData(ClipboardData(text: webAccessUrl.value));
  }

  /// 重新用AI分析所有网页
  void reAnalyzeAllWebpages() {
    // 获取所有文章
    ArticleRepository.updateEmptyStatusToPending();
    // ArticleRepository.updateAllStatusToCompleted();
  }
}
