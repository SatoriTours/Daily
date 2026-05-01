import 'package:daily_satori/app/services/logger_service.dart';

/// 迁移任务抽象类 - 所有迁移任务的基类
abstract class MigrationTask {
  /// 版本号，唯一且递增
  int get version;

  /// 任务描述
  String get description;

  /// 检查是否需要执行该迁移任务
  Future<bool> shouldRun() async => true;

  /// 执行迁移任务
  Future<void> migrate();

  /// 任务日志前缀
  String get _logPrefix => "🔄 [迁移-v$version]";

  /// 记录信息日志
  void logInfo(String message) {
    logger.i("$_logPrefix $message");
  }

  /// 记录警告日志
  void logWarning(String message) {
    logger.w("⚠️ $_logPrefix $message");
  }

  /// 记录错误日志
  void logError(String message, {dynamic error, StackTrace? stackTrace}) {
    logger.e("❌ $_logPrefix $message: $error", stackTrace: stackTrace);
  }

  /// 记录成功日志
  void logSuccess(String message) {
    logger.i("✅ $_logPrefix $message");
  }
}

/// 迁移计数器，用于统计迁移进度
class MigrationCounter {
  int migratedCount = 0; // 成功迁移数量
  int errorCount = 0; // 错误数量
  int noImageCount = 0; // 无图片数量
  int skippedCount = 0; // 跳过数量

  // 计算总处理数量
  int get totalProcessed =>
      migratedCount + errorCount + noImageCount + skippedCount;
}
