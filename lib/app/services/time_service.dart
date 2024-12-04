import 'package:get_time_ago/get_time_ago.dart';

import 'package:daily_satori/app/services/logger_service.dart';

class TimeService {
  // 单例模式
  TimeService._();
  static final TimeService _instance = TimeService._();
  static TimeService get i => _instance;

  /// 初始化时间服务
  Future<void> init() async {
    logger.i("[初始化服务] TimeService");
    // 设置相对时间本地化为中文
    GetTimeAgo.setDefaultLocale('zh');
  }
}
