import 'package:daily_satori/app/services/settings_service.dart';
import 'package:daily_satori/global.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';

class SettingsController extends GetxController {
  TextEditingController openaiAddress = TextEditingController();
  TextEditingController openaiToken = TextEditingController();
  TextEditingController backupDir = TextEditingController();

  Future<void> save() async {
    SettingsService.instance.saveSettings({
      SettingsService.openAIAddressKey: openaiAddress.text,
      SettingsService.openAITokenKey: openaiToken.text,
      SettingsService.backupDirKey: backupDir.text,
    });
  }

  void initData() {
    openaiAddress.text = SettingsService.instance.getSetting(SettingsService.openAIAddressKey);
    openaiToken.text = SettingsService.instance.getSetting(SettingsService.openAITokenKey);
    backupDir.text = SettingsService.instance.getSetting(SettingsService.backupDirKey);
  }

  Future<void> selectBackupDirectory() async {
    if (await _requestDirectoryPermissions()) {
      logger.i("弹出文件选择");
      backupDir.text = await FilePicker.platform.getDirectoryPath() ?? "";
    }
  }

  Future<bool> _requestDirectoryPermissions() async {
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();

    return manageExternalStoragePermission.isGranted;
  }
}
