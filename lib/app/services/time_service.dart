import 'package:daily_satori/app/services/logger_service.dart';
import 'package:get_time_ago/get_time_ago.dart';

class TimeService {
  TimeService._privateConstructor();
  static final TimeService _instance = TimeService._privateConstructor();
  static TimeService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] TimeService");
    GetTimeAgo.setDefaultLocale('zh');
  }
}
