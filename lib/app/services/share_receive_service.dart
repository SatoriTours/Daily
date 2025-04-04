import 'package:flutter/services.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';

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

    // 从文本中提取URL
    final url = _extractUrlFromText(sharedText);
    if (url.isEmpty) return;

    // 直接处理分享内容
    await Get.toNamed(
      Routes.shareDialog,
      arguments: {'articleID': 0, 'shareURL': url, 'update': false, 'needBackToApp': true},
    );
  }

  /// 从文本中提取URL
  String _extractUrlFromText(String text) {
    // 简单实现从文本中提取URL的逻辑
    final urlRegex = RegExp(r'https?://[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?');
    final match = urlRegex.firstMatch(text);
    return match != null ? match.group(0) ?? '' : '';
  }
}
