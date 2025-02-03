import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';

class ShareReceiveService {
  // 单例模式
  ShareReceiveService._();
  static final ShareReceiveService _instance = ShareReceiveService._();
  static ShareReceiveService get i => _instance;

  // 定义与原生平台通信的通道
  static const _platform = MethodChannel('tours.sator.daily/share');

  /// 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] ShareReceiveService");
    _registerShareReceiveHandler();
  }

  /// 注册分享接收处理器
  void _registerShareReceiveHandler() {
    _platform.setMethodCallHandler(_handleMethodCall);
  }

  /// 处理来自原生平台的方法调用
  Future<void> _handleMethodCall(MethodCall call) async {
    logger.i("接收到原生平台消息: ${call.method}");

    if (call.method == 'receiveSharedText') {
      await _handleSharedText(call.arguments);
    }
  }

  /// 处理分享的文本内容
  Future<void> _handleSharedText(String sharedText) async {
    logger.i("处理分享内容, 参数: $sharedText");

    final url = getUrlFromText(sharedText);
    if (url.isEmpty) return;

    // 确保数据库未处于迁移状态
    if (!(await ObjectboxService.i.shouldMigrateFromSQLite())) {
      await Get.toNamed(
        Routes.SHARE_DIALOG,
        arguments: {
          'articleID': 0,
          'shareURL': url,
          'update': false,
        },
      );
    }
  }
}
