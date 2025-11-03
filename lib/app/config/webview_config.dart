/// WebView配置
class WebViewConfig {
  WebViewConfig._();

  static const Duration timeout = Duration(seconds: 25); // WebView超时时间
  static const Duration sessionMaxLifetime = Duration(minutes: 4); // 会话最大生命周期
  static const int maxConcurrentSessions = 2; // 最大并发会话数
  static const int maxRedirects = 10; // 最大重定向次数
}
