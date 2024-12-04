import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';
import 'package:intl/intl.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 本地化时间设置
final String timeLocal = 'zh_CN';

/// 是否为生产环境
bool get isProduction => const bool.fromEnvironment("dart.vm.product");

/// 获取当前时间的ISO 8601格式字符串
String nowToString() => DateTime.now().toIso8601String();

/// 更新数据的时间戳
Map<String, String?> updateTimestamps(Map<String, String?> data) {
  final currentTime = nowToString();
  data['updated_at'] = currentTime;
  data['created_at'] ??= currentTime;
  return data;
}

/// 检查文本是否包含中文字符
bool isChinese(String text) => RegExp(r'[\u4e00-\u9fa5]').hasMatch(text);

/// 获取文本的子串,可指定长度和后缀
String getSubstring(String text, {int length = 50, String suffix = ''}) {
  if (length < 0) throw ArgumentError('length不能为负数');
  return text.length > length ? '${text.substring(0, length)}$suffix' : text;
}

/// 获取文本的第一行
String firstLine(String text) => text.split('\n').first;

/// 从主机名获取顶级域名
String getTopLevelDomain(String? host) {
  if (host == null) return '';

  final parts = host.split('.');
  if (parts.length < 2) return host;

  return '${parts[parts.length - 2]}.${parts.last}';
}

/// 格式化日期时间为本地格式
String formatDateTimeToLocal(DateTime dateTime) {
  try {
    return DateFormat('yyyy-MM-dd HH:mm:ss').format(dateTime.toLocal());
  } catch (e) {
    logger.d("日期格式转换失败 $e");
    return '';
  }
}

/// 显示成功提示
void successNotice(String content, {String title = '提示'}) {
  Get.snackbar(title, content, snackPosition: SnackPosition.top, backgroundColor: Colors.green);
}

/// 显示错误提示
void errorNotice(String content, {String title = '错误'}) {
  Get.snackbar(title, content, snackPosition: SnackPosition.top, backgroundColor: Colors.red);
}

/// 显示全屏加载提示
void showFullScreenLoading({String tips = '', Color barrierColor = Colors.transparent}) {
  Get.dialog(
    PopScope(
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(tips, style: MyFontStyle.loadingTipsStyle),
          ],
        ),
      ),
    ),
    barrierDismissible: false,
    barrierColor: barrierColor,
  );
}

/// 显示确认对话框
Future<void> showConfirmationDialog(
  String title,
  String message, {
  String confirmText = '同意',
  String cancelText = '取消',
  Function()? onConfirmed,
  Function()? onCanceled,
}) async {
  await Get.dialog<bool>(
    AlertDialog(
      title: Text(title),
      content: Text(message),
      actions: [
        TextButton(
          onPressed: () {
            Get.close();
            onCanceled?.call();
          },
          child: Text(cancelText),
        ),
        TextButton(
          onPressed: () {
            Get.close();
            onConfirmed?.call();
          },
          child: Text(confirmText),
        ),
      ],
    ),
  );
}

/// 获取剪贴板文本
Future<String> getClipboardText() async {
  final data = await Clipboard.getData(Clipboard.kTextPlain);
  return data?.text ?? '';
}

/// 设置剪贴板文本
Future<void> setClipboardText(String text) async {
  await Clipboard.setData(ClipboardData(text: text));
}

/// 从文本中提取URL
String getUrlFromText(String text) {
  final urlPattern = RegExp(
    r'https?:\/\/[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&\/=]*)',
    caseSensitive: false,
  );

  final url = urlPattern.firstMatch(text)?.group(0) ?? '';
  if (url.startsWith('http://')) {
    final httpsUrl = url.replaceFirst('http://', 'https://');
    logger.i("[checkClipboardText] 将 http 链接替换为 https: $httpsUrl");
    return httpsUrl;
  }
  return url;
}
