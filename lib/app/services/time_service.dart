import 'package:get_time_ago/get_time_ago.dart';

import 'package:daily_satori/app/services/service_base.dart';

class TimeService extends AppService {
  // 单例模式
  TimeService._();
  static final TimeService _instance = TimeService._();
  static TimeService get i => _instance;

  @override
  ServicePriority get priority => ServicePriority.critical;

  /// 初始化时间服务
  @override
  Future<void> init() async {
    // 设置相对时间本地化为中文
    GetTimeAgo.setDefaultLocale('zh');
  }

  @override
  void dispose() {}
}
