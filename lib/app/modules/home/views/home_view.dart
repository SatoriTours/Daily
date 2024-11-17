import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/global.dart';

class HomeView extends StatelessWidget {
  static const platform = MethodChannel('tours.sator.daily/share');
  const HomeView({super.key});

  @override
  Widget build(BuildContext context) {
    bool hasSharedUrl = false;

    platform.setMethodCallHandler((call) async {
      logger.i("接收到原生 android 的消息: ${call.method}");
      if (call.method == 'receiveSharedText') {
        logger.i("跳转到: ${Routes.SHARE_DIALOG}, 接收到参数: ${call.arguments}");
        final url = getUrlFromText(call.arguments);
        if (url.isNotEmpty) {
          hasSharedUrl = true;
          WidgetsBinding.instance.addPostFrameCallback((_) {
            Get.offNamed(Routes.SHARE_DIALOG, arguments: {'shareURL': url});
          });
        }
      }
    });

    // 延迟一下检查是否有分享链接,如果没有就跳转到文章列表
    Future.delayed(Duration(milliseconds: 100), () {
      if (!hasSharedUrl) {
        Get.offNamed(Routes.ARTICLES);
      }
    });

    return Container();
  }
}
