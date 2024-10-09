import 'package:logger/logger.dart';

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
