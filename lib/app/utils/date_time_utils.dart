import 'package:intl/intl.dart';

import 'package:daily_satori/app/services/logger_service.dart';

/// 日期时间工具类
class DateTimeUtils {
  // 私有构造函数，防止实例化
  DateTimeUtils._();

  /// 获取当前时间的ISO 8601格式字符串
  static String nowToString() => DateTime.now().toIso8601String();

  /// 更新数据的时间戳
  static Map<String, String?> updateTimestamps(Map<String, String?> data) {
    final currentTime = nowToString();
    data['updated_at'] = currentTime;
    data['created_at'] ??= currentTime;
    return data;
  }

  /// 格式化日期时间为本地格式
  static String formatDateTimeToLocal(DateTime dateTime) {
    try {
      return DateFormat('yyyy-MM-dd HH:mm:ss').format(dateTime.toLocal());
    } catch (e) {
      logger.d("日期格式转换失败 $e");
      return '';
    }
  }
}
