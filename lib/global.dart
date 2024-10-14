import 'package:logger/logger.dart';
import 'package:intl/intl.dart';

late Logger logger;

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

String getSubstring(String text, {int length = 50}) {
  if (length < 0) {
    throw ArgumentError('length不能为负数');
  }
  return text.length > length ? text.substring(0, length) : text;
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
