import 'package:daily_satori/app/services/settings_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SettingsController extends GetxController {
  TextEditingController openaiAddress = TextEditingController();
  TextEditingController openaiToken = TextEditingController();

  Future<void> save() async {
    SettingsService.instance.saveSettings({
      'openai_address': openaiAddress.text,
      'openai_token': openaiToken.text,
    });
  }

  void initData(){
    openaiAddress.text = SettingsService.instance.getSetting('openai_address');
    openaiToken.text = SettingsService.instance.getSetting('openai_token');
  }
}
