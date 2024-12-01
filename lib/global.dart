import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';
import 'package:intl/intl.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/styles/font_style.dart';

final String timeLocal = 'zh_CN';

bool get isProduction => const bool.fromEnvironment("dart.vm.product");

String nowToString() {
  return DateTime.now().toIso8601String();
}

Map<String, String?> updateTimestamps(Map<String, String?> data) {
  String currentTime = nowToString();
  data['updated_at'] = currentTime;
  if (!data.containsKey('created_at') || data['created_at'] == null) {
    data['created_at'] = currentTime;
  }
  return data;
}

bool isChinese(String text) {
  return RegExp(r'[\u4e00-\u9fa5]').hasMatch(text);
}

String getSubstring(String text, {int length = 50, String suffix = ''}) {
  if (length < 0) {
    throw ArgumentError('length不能为负数');
  }
  return text.length > length ? text.substring(0, length) + suffix : text;
}

String firstLine(String text) {
  return text.split('\n').first;
}

String getTopLevelDomain(String? host) {
  if (host == null) {
    return "";
  }

  // 分割主机名以获取一级域名
  List<String> parts = host.split('.');

  // 检查域名部分的数量
  if (parts.length < 2) {
    return host; // 如果没有足够的部分，返回原始主机名
  }

  // 返回最后两个部分作为一级域名
  return '${parts[parts.length - 2]}.${parts[parts.length - 1]}';
}

String formatDateTimeToLocal(DateTime dateTime) {
  try {
    DateTime localDateTime = dateTime.toLocal();
    return DateFormat('yyyy-MM-dd HH:mm:ss').format(localDateTime);
  } catch (e) {
    logger.d("日期格式转换失败 $e");
  }
  return "";
}

void successNotice(String content, {String title = '提示'}) {
  Get.snackbar(title, content,
      snackPosition: SnackPosition.top, backgroundColor: Colors.green);
}

void errorNotice(String content, {String title = '错误'}) {
  Get.snackbar(title, content,
      snackPosition: SnackPosition.top, backgroundColor: Colors.red);
}

void showFullScreenLoading(
    {String tips = '', Color barrierColor = Colors.transparent}) {
  Get.dialog(
    PopScope(
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text(tips, style: MyFontStyle.loadingTipsStyle),
          ],
        ),
      ),
    ),
    barrierDismissible: false, // 点击外部区域不关闭对话框
    barrierColor: barrierColor,
  );
}

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

Future<String> getClipboardText() async {
  ClipboardData? data = await Clipboard.getData(Clipboard.kTextPlain);
  return data?.text ?? '';
}

Future<void> setClipboardText(String text) async {
  await Clipboard.setData(ClipboardData(text: text));
}

String getUrlFromText(String text) {
  final urlPattern = RegExp(
    r'https?:\/\/[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&\/=]*)',
    caseSensitive: false,
  );

  String url = urlPattern.firstMatch(text)?.group(0) ?? '';
  if (url.startsWith('http://')) {
    url = url.replaceFirst('http://', 'https://');
    logger.i("[checkClipboardText] 将 http 链接替换为 https: $url");
  }
  return url;
}
