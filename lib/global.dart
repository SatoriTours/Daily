import 'package:flutter/material.dart';

import 'package:daily_satori/app/utils/utils.dart';

// 导出所有工具类，使其他文件可以通过 global.dart 访问
export 'package:daily_satori/app/utils/utils.dart';

/// 以下函数保留用于向后兼容
/// 建议在新代码中直接使用对应的工具类

/// 本地化时间设置 (已移至 DateTimeUtils.timeLocal)
final String timeLocal = DateTimeUtils.timeLocal;

/// 是否为生产环境 (已移至 AppInfoUtils.isProduction)
bool get isProduction => AppInfoUtils.isProduction;

/// 获取当前时间的ISO 8601格式字符串 (已移至 DateTimeUtils.nowToString())
String nowToString() => DateTimeUtils.nowToString();

/// 获取当前应用版本信息 (已移至 AppInfoUtils.getVersion())
Future<String> getAppVersion() => AppInfoUtils.getVersion();

/// 更新数据的时间戳 (已移至 DateTimeUtils.updateTimestamps())
Map<String, String?> updateTimestamps(Map<String, String?> data) => DateTimeUtils.updateTimestamps(data);

/// 检查文本是否包含中文字符 (已移至 StringUtils.isChinese())
bool isChinese(String text) => StringUtils.isChinese(text);

/// 获取文本的子串,可指定长度和后缀 (已移至 StringUtils.getSubstring())
String getSubstring(String text, {int length = 50, String suffix = ''}) =>
    StringUtils.getSubstring(text, length: length, suffix: suffix);

/// 获取文本的第一行 (已移至 StringUtils.firstLine())
String firstLine(String text) => StringUtils.firstLine(text);

/// 从主机名获取顶级域名 (已移至 StringUtils.getTopLevelDomain())
String getTopLevelDomain(String? host) => StringUtils.getTopLevelDomain(host);

/// 格式化日期时间为本地格式 (已移至 DateTimeUtils.formatDateTimeToLocal())
String formatDateTimeToLocal(DateTime dateTime) => DateTimeUtils.formatDateTimeToLocal(dateTime);

/// 显示成功提示 (已移至 UIUtils.showSuccess())
void successNotice(String content, {String title = '提示'}) => UIUtils.showSuccess(content, title: title);

/// 显示错误提示 (已移至 UIUtils.showError())
void errorNotice(String content, {String title = '错误'}) => UIUtils.showError(content, title: title);

/// 显示全屏加载提示 (已移至 UIUtils.showLoading())
void showFullScreenLoading({String tips = '', Color barrierColor = Colors.black45}) =>
    UIUtils.showLoading(tips: tips, barrierColor: barrierColor);

/// 显示确认对话框 (已移至 DialogUtils.showConfirm())
Future<void> showConfirmationDialog(
  String title,
  String message, {
  String confirmText = '同意',
  String cancelText = '取消',
  Function()? onConfirmed,
  Function()? onCanceled,
}) async {
  await DialogUtils.showConfirm(
    title: title,
    message: message,
    confirmText: confirmText,
    cancelText: cancelText,
    onConfirm: onConfirmed,
    onCancel: onCanceled,
  );
}

/// 获取剪贴板文本 (已移至 ClipboardUtils.getText())
Future<String> getClipboardText() => ClipboardUtils.getText();

/// 设置剪贴板文本 (已移至 ClipboardUtils.setText())
Future<void> setClipboardText(String text) => ClipboardUtils.setText(text);

/// 从文本中提取URL (已移至 StringUtils.getUrlFromText())
String getUrlFromText(String text) => StringUtils.getUrlFromText(text);
