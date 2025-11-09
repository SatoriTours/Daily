/// WebView配置
class WebViewConfig {
  WebViewConfig._();

  static const Duration timeout = Duration(seconds: 25); // WebView超时时间
  static const Duration sessionMaxLifetime = Duration(minutes: 4); // 会话最大生命周期
  static const int maxConcurrentSessions = 2; // 最大并发会话数
  static const int maxRedirects = 10; // 最大重定向次数

  // DOM相关配置
  static const Duration domStabilityCheckDelay = Duration(milliseconds: 1500); // DOM稳定性检查延迟
  static const Duration loadProgressCheckDelay = Duration(seconds: 4); // 加载进度检查延迟
  static const Duration screenshotDelay = Duration(milliseconds: 100); // 截图延迟
}
