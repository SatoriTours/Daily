import 'package:daily_satori/app/services/settings_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SettingsController extends GetxController {
  TextEditingController openaiAddress = TextEditingController();
  TextEditingController openaiToken = TextEditingController();

  Future<void> save() async {
    SettingsService.instance.saveSettings({
      SettingsService.openAIAddressKey: openaiAddress.text,
      SettingsService.openAITokenKey: openaiToken.text,
    });
  }

  void initData(){
    openaiAddress.text = SettingsService.instance.getSetting(SettingsService.openAIAddressKey);
    openaiToken.text = SettingsService.instance.getSetting(SettingsService.openAITokenKey);
  }
}
