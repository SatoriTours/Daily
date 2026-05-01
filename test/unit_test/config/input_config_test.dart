import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/input_config.dart';

void main() {
  group('InputConfig', () {
    test('should have positive length limits', () {
      expect(InputConfig.maxLength > 0, isTrue);
      expect(InputConfig.commentMaxLength > 0, isTrue);
      expect(InputConfig.searchMinLength > 0, isTrue);
    });

    test('should have valid line limits', () {
      expect(InputConfig.maxLines > 0, isTrue);
      expect(InputConfig.minLines > 0, isTrue);
      expect(InputConfig.minLines <= InputConfig.maxLines, isTrue);
    });

    test('maxLength should be greater than searchMinLength', () {
      expect(InputConfig.maxLength > InputConfig.searchMinLength, isTrue);
    });

    test('commentMaxLength should be greater than maxLength', () {
      expect(InputConfig.commentMaxLength > InputConfig.maxLength, isTrue);
    });

    test('should have reasonable values', () {
      expect(InputConfig.maxLength, equals(120));
      expect(InputConfig.maxLines, equals(8));
      expect(InputConfig.minLines, equals(1));
      expect(InputConfig.commentMaxLength, equals(500));
      expect(InputConfig.searchMinLength, equals(2));
    });
  });
}
