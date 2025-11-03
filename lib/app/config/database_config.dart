/// 数据库配置
class DatabaseConfig {
  DatabaseConfig._();

  static const int version = 1; // 数据库版本
  static const String name = 'daily_satori.db'; // 数据库名称
  static const int maxSize = 100 * 1024 * 1024; // 最大数据库大小 100MB
  static const String objectBoxDir = 'obx-daily'; // ObjectBox目录名
}
