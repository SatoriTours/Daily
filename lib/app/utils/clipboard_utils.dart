import 'package:flutter/services.dart';

/// 剪贴板工具类
class ClipboardUtils {
  // 私有构造函数，防止实例化
  ClipboardUtils._();

  /// 获取剪贴板文本
  static Future<String> getText() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    return data?.text ?? '';
  }

  /// 设置剪贴板文本
  static Future<void> setText(String text) async {
    await Clipboard.setData(ClipboardData(text: text));
  }
}
