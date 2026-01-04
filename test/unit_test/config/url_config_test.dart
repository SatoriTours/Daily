import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/url_config.dart';

void main() {
  group('UrlConfig', () {
    test('should have GitHub release API URL', () {
      expect(UrlConfig.githubReleaseApi.isNotEmpty, isTrue);
      expect(UrlConfig.githubReleaseApi.contains('api.github.com'), isTrue);
      expect(UrlConfig.githubReleaseApi.contains('Daily'), isTrue);
    });

    test('should have GitHub release API mirror URL', () {
      expect(UrlConfig.githubReleaseApiMirror.isNotEmpty, isTrue);
      expect(UrlConfig.githubReleaseApiMirror.contains('mirror.ghproxy.com'), isTrue);
    });

    test('should have easylist URL', () {
      expect(UrlConfig.easylistUrl.isNotEmpty, isTrue);
      expect(UrlConfig.easylistUrl.startsWith('https://'), isTrue);
      expect(UrlConfig.easylistUrl.endsWith('.txt'), isTrue);
    });

    test('should have local easylist file path', () {
      expect(UrlConfig.localEasylistFile.isNotEmpty, isTrue);
      expect(UrlConfig.localEasylistFile.startsWith('assets/'), isTrue);
    });

    test('should have font license path', () {
      expect(UrlConfig.fontLicensePath.isNotEmpty, isTrue);
      expect(UrlConfig.fontLicensePath.startsWith('assets/'), isTrue);
      expect(UrlConfig.fontLicensePath.endsWith('OFL.txt'), isTrue);
    });
  });
}
