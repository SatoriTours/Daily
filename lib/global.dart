import 'package:flutter/material.dart';

import 'package:daily_satori/app/utils/utils.dart';

// 导出所有工具类，使其他文件可以通过 global.dart 访问
export 'package:daily_satori/app/utils/utils.dart';

/// 以下函数保留用于向后兼容
/// 建议在新代码中直接使用对应的工具类

/// 本地化时间设置
@Deprecated('使用 DateTimeUtils.timeLocal 替代')
final String timeLocal = DateTimeUtils.timeLocal;

/// 是否为生产环境
@Deprecated('使用 AppInfoUtils.isProduction 替代')
bool get isProduction => AppInfoUtils.isProduction;

/// 获取当前时间的ISO 8601格式字符串
@Deprecated('使用 DateTimeUtils.nowToString() 替代')
String nowToString() => DateTimeUtils.nowToString();

/// 获取当前应用版本信息
@Deprecated('使用 AppInfoUtils.getVersion() 替代')
Future<String> getAppVersion() => AppInfoUtils.getVersion();

/// 更新数据的时间戳
@Deprecated('使用 DateTimeUtils.updateTimestamps() 替代')
Map<String, String?> updateTimestamps(Map<String, String?> data) => DateTimeUtils.updateTimestamps(data);

/// 检查文本是否包含中文字符
@Deprecated('使用 StringUtils.isChinese() 替代')
bool isChinese(String text) => StringUtils.isChinese(text);

/// 获取文本的子串,可指定长度和后缀
@Deprecated('使用 StringUtils.getSubstring() 替代')
String getSubstring(String text, {int length = 50, String suffix = ''}) =>
    StringUtils.getSubstring(text, length: length, suffix: suffix);

/// 获取文本的第一行
@Deprecated('使用 StringUtils.firstLine() 替代')
String firstLine(String text) => StringUtils.firstLine(text);

/// 从主机名获取顶级域名
@Deprecated('使用 StringUtils.getTopLevelDomain() 替代')
String getTopLevelDomain(String? host) => StringUtils.getTopLevelDomain(host);

/// 格式化日期时间为本地格式
@Deprecated('使用 DateTimeUtils.formatDateTimeToLocal() 替代')
String formatDateTimeToLocal(DateTime dateTime) => DateTimeUtils.formatDateTimeToLocal(dateTime);

/// 显示成功提示
@Deprecated('使用 UIUtils.showSuccess() 替代')
void successNotice(String content, {String title = '提示'}) => UIUtils.showSuccess(content, title: title);

/// 显示错误提示
@Deprecated('使用 UIUtils.showError() 替代')
void errorNotice(String content, {String title = '错误'}) => UIUtils.showError(content, title: title);

/// 显示全屏加载提示
@Deprecated('使用 UIUtils.showLoading() 替代')
void showFullScreenLoading({String tips = '', Color barrierColor = Colors.black45}) =>
    UIUtils.showLoading(tips: tips, barrierColor: barrierColor);

/// 显示确认对话框
@Deprecated('使用 DialogUtils.showConfirm() 替代')
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

/// 获取剪贴板文本
@Deprecated('使用 ClipboardUtils.getText() 替代')
Future<String> getClipboardText() => ClipboardUtils.getText();

/// 设置剪贴板文本
@Deprecated('使用 ClipboardUtils.setText() 替代')
Future<void> setClipboardText(String text) => ClipboardUtils.setText(text);

/// 从文本中提取URL
@Deprecated('使用 StringUtils.getUrlFromText() 替代')
String getUrlFromText(String text) => StringUtils.getUrlFromText(text);
