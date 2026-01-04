import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/pagination_config.dart';

void main() {
  group('PaginationConfig', () {
    test('should have valid page sizes', () {
      expect(PaginationConfig.defaultPageSize > 0, isTrue);
      expect(PaginationConfig.maxPageSize > 0, isTrue);
      expect(PaginationConfig.minPageSize > 0, isTrue);
    });

    test('defaultPageSize should be within valid range', () {
      expect(PaginationConfig.defaultPageSize >= PaginationConfig.minPageSize, isTrue);
      expect(PaginationConfig.defaultPageSize <= PaginationConfig.maxPageSize, isTrue);
    });

    test('minPageSize should be less than or equal to default', () {
      expect(PaginationConfig.minPageSize <= PaginationConfig.defaultPageSize, isTrue);
    });

    test('maxPageSize should be greater than or equal to default', () {
      expect(PaginationConfig.maxPageSize >= PaginationConfig.defaultPageSize, isTrue);
    });

    test('should have reasonable values', () {
      expect(PaginationConfig.defaultPageSize, equals(20));
      expect(PaginationConfig.maxPageSize, equals(100));
      expect(PaginationConfig.minPageSize, equals(5));
    });
  });
}
