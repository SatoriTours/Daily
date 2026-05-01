import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/web_service_config.dart';

void main() {
  group('WebServiceConfig', () {
    test('should have valid HTTP port', () {
      expect(WebServiceConfig.httpPort > 0, isTrue);
      expect(WebServiceConfig.httpPort <= 65535, isTrue);
    });

    test('should have reasonable port number', () {
      expect(WebServiceConfig.httpPort, equals(8888));
    });

    test('port should be non-privileged', () {
      expect(WebServiceConfig.httpPort > 1024, isTrue);
    });
  });
}
