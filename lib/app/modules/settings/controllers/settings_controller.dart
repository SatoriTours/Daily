import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';

class SettingsController extends GetxController {
  final webServiceAddress = ''.obs;

  @override
  void onInit() {
    super.onInit();
    _updateWebServerAddress();
  }

  void _updateWebServerAddress() async {
    webServiceAddress.value = await WebService.i.getAppAddress();
  }

  Future<void> selectBackupDirectory() async {
    if (await _requestDirectoryPermissions()) {
      final backupDir = await FilePicker.platform.getDirectoryPath(
        dialogTitle: "选择app备份目录",
        initialDirectory: SettingService.i.getSetting(SettingService.backupDirKey),
      );
      if (backupDir != null) {
        logger.i("选择了备份路径 $backupDir");
        SettingService.i.saveSetting(SettingService.backupDirKey, backupDir);
      }
    }
  }

  Future<bool> _requestDirectoryPermissions() async {
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();
    return manageExternalStoragePermission.isGranted;
  }

  void copyWebServiceAddress() {
    Clipboard.setData(ClipboardData(text: webServiceAddress.value));
  }
}
