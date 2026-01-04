import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/search_config.dart';

void main() {
  group('SearchConfig', () {
    test('should have positive debounce time', () {
      expect(SearchConfig.debounceTime.inMilliseconds > 0, isTrue);
    });

    test('should have positive min length', () {
      expect(SearchConfig.minLength > 0, isTrue);
    });

    test('should have positive max length', () {
      expect(SearchConfig.maxLength > 0, isTrue);
    });

    test('debounce time should be reasonable', () {
      expect(SearchConfig.debounceTime.inMilliseconds, equals(300));
    });

    test('minLength should be less than maxLength', () {
      expect(SearchConfig.minLength < SearchConfig.maxLength, isTrue);
    });

    test('should have reasonable length limits', () {
      expect(SearchConfig.minLength, equals(2));
      expect(SearchConfig.maxLength, equals(100));
    });
  });
}
