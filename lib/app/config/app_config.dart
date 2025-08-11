/// 应用程序配置中心
/// 集中管理所有业务常量和配置
class AppConfig {
  // 备份相关配置
  static const int productionBackupIntervalHours = 6;
  static const int developmentBackupIntervalHours = 24;
  
  // WebView相关配置
  static const Duration webViewTimeout = Duration(seconds: 25);
  static const Duration sessionMaxLifetime = Duration(minutes: 4);
  static const int maxConcurrentSessions = 2;
  
  // 分页配置
  static const int defaultPageSize = 20;
  static const int maxPageSize = 100;
  
  // 搜索配置
  static const Duration searchDebounceTime = Duration(milliseconds: 300);
  static const int minSearchLength = 2;
  
  // 图片配置
  static const int maxImageUploadSize = 5 * 1024 * 1024; // 5MB
  static const Duration imageCacheDuration = Duration(days: 7);
  
  // AI配置
  static const Duration aiTimeout = Duration(seconds: 30);
  static const int maxSummaryLength = 500;
  static const int maxContentLength = 10000;
  
  // 缓存配置
  static const Duration cacheExpiration = Duration(hours: 24);
  static const int maxCacheSize = 50 * 1024 * 1024; // 50MB
}