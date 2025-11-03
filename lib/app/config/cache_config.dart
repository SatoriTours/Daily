/// 缓存配置
class CacheConfig {
  CacheConfig._();

  static const Duration expiration = Duration(hours: 24); // 缓存过期时间
  static const int maxSize = 50 * 1024 * 1024; // 最大缓存大小 50MB
  static const int maxEntries = 1000; // 最大缓存条目数
}
