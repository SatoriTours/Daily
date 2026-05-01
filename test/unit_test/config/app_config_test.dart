import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/app_config.dart';

void main() {
  group('AppConfig', () {
    test('should export all config files', () {
      expect(AIConfig.timeout, isNotNull);
      expect(BackupConfig.interval, isNotNull);
      expect(CacheConfig.expiration, isNotNull);
      expect(DatabaseConfig.version, isNotNull);
      expect(DateFormatConfig.display, isNotNull);
      expect(DirectoryConfig.backup, isNotNull);
      expect(DownloadConfig.defaultReceiveTimeout, isNotNull);
      expect(ImageConfig.maxUploadSize, isNotNull);
      expect(InputConfig.maxLength, isNotNull);
      expect(MessageConfig.errorNetwork, isNotNull);
      expect(NetworkConfig.timeout, isNotNull);
      expect(PaginationConfig.defaultPageSize, isNotNull);
      expect(RegexConfig.url, isNotNull);
      expect(SearchConfig.debounceTime, isNotNull);
      expect(SessionConfig.expireTime, isNotNull);
      expect(UrlConfig.githubReleaseApi, isNotNull);
      expect(WebServiceConfig.httpPort, isNotNull);
      expect(WebViewConfig.timeout, isNotNull);
    });
  });
}
