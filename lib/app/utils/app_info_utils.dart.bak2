import 'package:package_info_plus/package_info_plus.dart';

import 'package:daily_satori/app/services/logger_service.dart';

/// 应用信息工具类
class AppInfoUtils {
  // 私有构造函数，防止实例化
  AppInfoUtils._();

  /// 是否为生产环境
  static bool get isProduction => const bool.fromEnvironment("dart.vm.product");

  /// 获取当前应用版本信息
  static Future<String> getVersion() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return 'v${packageInfo.version}';
      // return 'v${packageInfo.version} (${packageInfo.buildNumber})';
    } catch (e) {
      logger.e("获取应用版本失败: $e");
      return '未知版本';
    }
  }

  /// 获取应用包名
  static Future<String> getPackageName() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return packageInfo.packageName;
    } catch (e) {
      logger.e("获取应用包名失败: $e");
      return '';
    }
  }

  /// 获取应用名称
  static Future<String> getAppName() async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      return packageInfo.appName;
    } catch (e) {
      logger.e("获取应用名称失败: $e");
      return '';
    }
  }
}
