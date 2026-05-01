/// 网络配置
class NetworkConfig {
  NetworkConfig._();

  static const Duration timeout = Duration(seconds: 30); // 网络请求超时时间
  static const int maxRetries = 3; // 最大重试次数
  static const Duration retryDelay = Duration(seconds: 1); // 重试延迟时间
}
