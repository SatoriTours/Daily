import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/cache_config.dart';

void main() {
  group('CacheConfig', () {
    test('should have valid expiration duration', () {
      expect(CacheConfig.expiration.inHours, equals(24));
      expect(CacheConfig.expiration.inSeconds > 0, isTrue);
    });

    test('should have positive max size', () {
      expect(CacheConfig.maxSize > 0, isTrue);
      expect(CacheConfig.maxSize > 1024 * 1024, isTrue);
    });

    test('should have positive max entries', () {
      expect(CacheConfig.maxEntries > 0, isTrue);
    });

    test('maxSize should be in bytes', () {
      expect(CacheConfig.maxSize, equals(50 * 1024 * 1024));
    });
  });
}
