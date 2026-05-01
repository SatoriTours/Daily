import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/date_format_config.dart';

void main() {
  group('DateFormatConfig', () {
    test('should have valid format strings', () {
      expect(DateFormatConfig.display.isNotEmpty, isTrue);
      expect(DateFormatConfig.full.isNotEmpty, isTrue);
      expect(DateFormatConfig.iso.isNotEmpty, isTrue);
      expect(DateFormatConfig.file.isNotEmpty, isTrue);
    });

    test('display format should be yyyy-MM-dd', () {
      expect(DateFormatConfig.display, equals('yyyy-MM-dd'));
    });

    test('full format should include time', () {
      expect(DateFormatConfig.full.contains('HH:mm:ss'), isTrue);
    });

    test('iso format should include T and Z', () {
      expect(DateFormatConfig.iso.contains('T'), isTrue);
      expect(DateFormatConfig.iso.contains('Z'), isTrue);
    });

    test('locale should be zh_CN', () {
      expect(DateFormatConfig.locale, equals('zh_CN'));
    });
  });
}
