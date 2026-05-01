import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/download_config.dart';

void main() {
  group('DownloadConfig', () {
    test('should have positive timeout durations', () {
      expect(DownloadConfig.defaultReceiveTimeout.inMinutes, equals(15));
      expect(DownloadConfig.defaultSendTimeout.inMinutes, equals(2));
      expect(DownloadConfig.imageReceiveTimeout.inMinutes, equals(5));
      expect(DownloadConfig.imageSendTimeout.inMinutes, equals(1));
    });

    test('default receive timeout should be longer than send timeout', () {
      expect(
        DownloadConfig.defaultReceiveTimeout >
            DownloadConfig.defaultSendTimeout,
        isTrue,
      );
    });

    test('image receive timeout should be longer than send timeout', () {
      expect(
        DownloadConfig.imageReceiveTimeout > DownloadConfig.imageSendTimeout,
        isTrue,
      );
    });

    test('all timeouts should be positive', () {
      expect(DownloadConfig.defaultReceiveTimeout.inSeconds > 0, isTrue);
      expect(DownloadConfig.defaultSendTimeout.inSeconds > 0, isTrue);
      expect(DownloadConfig.imageReceiveTimeout.inSeconds > 0, isTrue);
      expect(DownloadConfig.imageSendTimeout.inSeconds > 0, isTrue);
    });
  });
}
