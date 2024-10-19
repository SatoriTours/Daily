import 'package:flutter/material.dart';

import 'package:file_picker/file_picker.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:daily_satori/app/services/settings_service.dart';

class SettingsController extends GetxController {
  TextEditingController openaiAddress = TextEditingController();
  TextEditingController openaiToken = TextEditingController();
  TextEditingController backupDir = TextEditingController();

  Future<void> save() async {
    SettingsService.i.saveSettings({
      SettingsService.openAIAddressKey: openaiAddress.text,
      SettingsService.openAITokenKey: openaiToken.text,
      SettingsService.backupDirKey: backupDir.text,
    });
    Get.back();
  }

  void initData() {
    openaiAddress.text = SettingsService.i.getSetting(SettingsService.openAIAddressKey);
    openaiToken.text = SettingsService.i.getSetting(SettingsService.openAITokenKey);
    backupDir.text = SettingsService.i.getSetting(SettingsService.backupDirKey);
  }

  Future<void> selectBackupDirectory() async {
    if (await _requestDirectoryPermissions()) {
      backupDir.text = await FilePicker.platform.getDirectoryPath() ?? "";
    }
  }

  Future<bool> _requestDirectoryPermissions() async {
    final manageExternalStoragePermission = await Permission.manageExternalStorage.request();

    return manageExternalStoragePermission.isGranted;
  }
}
