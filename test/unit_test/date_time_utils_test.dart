import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/utils/date_time_utils.dart';

void main() {
  group('DateTimeUtils', () {
    group('nowToString', () {
      test('should return ISO 8601 formatted string', () {
        final result = DateTimeUtils.nowToString();
        expect(
          result,
          matches(RegExp(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}')),
        );
      });
    });

    group('updateTimestamps', () {
      test('should update createdAt and updatedAt', () {
        final data = <String, String?>{};
        DateTimeUtils.updateTimestamps(data);
        expect(data.containsKey('updated_at'), isTrue);
        expect(data.containsKey('created_at'), isTrue);
        expect(data['created_at'], isNotNull);
        expect(data['updated_at'], isNotNull);
      });

      test('should not overwrite existing created_at', () {
        const existingCreated = '2023-01-01T00:00:00';
        final data = <String, String?>{
          'created_at': existingCreated,
          'title': 'test',
        };
        DateTimeUtils.updateTimestamps(data);
        expect(data['created_at'], existingCreated);
        expect(data['updated_at'], isNotNull);
      });
    });

    group('formatDateTimeToLocal', () {
      test('should format datetime to local format', () {
        final dateTime = DateTime(2024, 1, 15, 10, 30, 0);
        final result = DateTimeUtils.formatDateTimeToLocal(dateTime);
        expect(result, contains('2024'));
        expect(result, contains('01'));
        expect(result, contains('15'));
      });

      test('should handle valid date string', () {
        final result = DateTimeUtils.formatDateTimeToLocal(
          DateTime.parse('2024-01-15'),
        );
        expect(result.isNotEmpty, isTrue);
      });
    });
  });
}
