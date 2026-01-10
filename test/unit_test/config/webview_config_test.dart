import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/webview_config.dart';

void main() {
  group('WebViewConfig', () {
    test('should have positive timeout', () {
      expect(WebViewConfig.timeout.inSeconds > 0, isTrue);
    });

    test('should have positive session max lifetime', () {
      expect(WebViewConfig.sessionMaxLifetime.inMinutes > 0, isTrue);
    });

    test('should have positive max concurrent sessions', () {
      expect(WebViewConfig.maxConcurrentSessions > 0, isTrue);
    });

    test('should have positive max redirects', () {
      expect(WebViewConfig.maxRedirects > 0, isTrue);
    });

    test('should have positive DOM stability check delay', () {
      expect(WebViewConfig.domStabilityCheckDelay.inMilliseconds > 0, isTrue);
    });

    test('should have positive load progress check delay', () {
      expect(WebViewConfig.loadProgressCheckDelay.inSeconds > 0, isTrue);
    });

    test('should have positive screenshot delay', () {
      expect(WebViewConfig.screenshotDelay.inMilliseconds > 0, isTrue);
    });

    test('should have reasonable values', () {
      expect(WebViewConfig.timeout.inSeconds, equals(25));
      expect(WebViewConfig.sessionMaxLifetime.inMinutes, equals(4));
      expect(WebViewConfig.maxConcurrentSessions, equals(2));
      expect(WebViewConfig.maxRedirects, equals(10));
      expect(WebViewConfig.domStabilityCheckDelay.inMilliseconds, equals(1500));
      expect(WebViewConfig.loadProgressCheckDelay.inSeconds, equals(4));
      expect(WebViewConfig.screenshotDelay.inMilliseconds, equals(100));
    });

    test('timeout should be greater than load progress check delay', () {
      expect(
        WebViewConfig.timeout > WebViewConfig.loadProgressCheckDelay,
        isTrue,
      );
    });
  });
}
