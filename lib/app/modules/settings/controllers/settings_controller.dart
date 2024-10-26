import 'package:daily_satori/global.dart';
import 'package:file_picker/file_picker.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:daily_satori/app/services/settings_service.dart';

class SettingsController extends GetxController {
  Future<void> selectBackupDirectory() async {
    if (await _requestDirectoryPermissions()) {
      final backupDir = await FilePicker.platform.getDirectoryPath(
        dialogTitle: "选择app备份目录",
        initialDirectory: SettingsService.i.getSetting(SettingsService.backupDirKey),
      );
      if (backupDir != null) {
        logger.i("选择了备份路径 $backupDir");
        SettingsService.i.saveSetting(SettingsService.backupDirKey, backupDir);
      }
    }
  }

  Future<bool> _requestDirectoryPermissions() async {
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();
    return manageExternalStoragePermission.isGranted;
  }
}
