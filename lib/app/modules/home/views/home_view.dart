import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

class HomeView extends StatelessWidget {
  static const platform = MethodChannel('tours.sator.daily/share');
  const HomeView({super.key});

  @override
  Widget build(BuildContext context) {
    platform.setMethodCallHandler((call) async {
      logger.i("接收到原生 android 的消息: ${call.method}");
      if (call.method == 'receiveSharedText') {
        logger.i("跳转到: ${Routes.SHARE_DIALOG}, 接收到参数: ${call.arguments}");
        WidgetsBinding.instance.addPostFrameCallback((_) {
          Get.offNamed(Routes.SHARE_DIALOG, arguments: {'shareURL': call.arguments});
        });
      }
    });
    Get.offNamed(Routes.ARTICLES);

    return Container();
  }
}
