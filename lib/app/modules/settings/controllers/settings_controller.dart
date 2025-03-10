import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';

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

  Future<bool> _requestDirectoryPermissions() async {
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();
    return manageExternalStoragePermission.isGranted;
  }

  void copyWebServiceAddress() {
    Clipboard.setData(ClipboardData(text: webServiceAddress.value));
  }

  void copyWebAccessUrl() {
    Clipboard.setData(ClipboardData(text: webAccessUrl.value));
  }
}
