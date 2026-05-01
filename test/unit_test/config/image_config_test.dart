import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/image_config.dart';

void main() {
  group('ImageConfig', () {
    test('should have valid max upload size', () {
      expect(ImageConfig.maxUploadSize, equals(5 * 1024 * 1024));
      expect(ImageConfig.maxUploadSize > 0, isTrue);
    });

    test('should have positive dimensions', () {
      expect(ImageConfig.maxWidth > 0, isTrue);
      expect(ImageConfig.maxHeight > 0, isTrue);
    });

    test('maxWidth should be greater than or equal to maxHeight', () {
      expect(ImageConfig.maxWidth >= ImageConfig.maxHeight, isTrue);
    });

    test('should have valid cache duration', () {
      expect(ImageConfig.cacheDuration.inDays, equals(7));
      expect(ImageConfig.cacheDuration.inSeconds > 0, isTrue);
    });

    test('should have valid download timeout', () {
      expect(ImageConfig.downloadTimeout.inSeconds, equals(30));
      expect(ImageConfig.downloadTimeout.inSeconds > 0, isTrue);
    });
  });
}
