import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';

class ShareReceiveService {
  ShareReceiveService._privateConstructor();
  static final ShareReceiveService _instance =
      ShareReceiveService._privateConstructor();
  static ShareReceiveService get i => _instance;

  static const platform = MethodChannel('tours.sator.daily/share');

  Future<void> init() async {
    logger.i("[初始化服务] ShareReceiveService");
    registerShareReceiveEvent();
  }

  void registerShareReceiveEvent() {
    platform.setMethodCallHandler((call) async {
      logger.i("接收到原生 android 的消息: ${call.method}");

      if (call.method == 'receiveSharedText') {
        logger.i("跳转到: ${Routes.SHARE_DIALOG}, 接收到参数: ${call.arguments}");
        final url = getUrlFromText(call.arguments);
        if (url.isNotEmpty) {
          // 如果数据库没有迁移, 则跳转到分享收藏页面
          if (!ObjectboxService.i.shouldMigrateFromSQLite()) {
            Get.toNamed(Routes.SHARE_DIALOG, arguments: {
              'articleID': 0,
              'shareURL': url,
              'update': false,
            });
          }
        }
      }
    });
  }
}
