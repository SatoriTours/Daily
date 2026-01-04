import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/data/weekly_summary/weekly_summary_model.dart';

void main() {
  group('WeeklySummaryStatus', () {
    test('should have 4 status values', () {
      expect(WeeklySummaryStatus.values.length, equals(4));
    });

    test('should have correct values', () {
      expect(WeeklySummaryStatus.pending.value, equals('pending'));
      expect(WeeklySummaryStatus.generating.value, equals('generating'));
      expect(WeeklySummaryStatus.completed.value, equals('completed'));
      expect(WeeklySummaryStatus.failed.value, equals('failed'));
    });

    group('fromValue', () {
      test('should return correct status for valid values', () {
        expect(
          WeeklySummaryStatus.fromValue('pending'),
          equals(WeeklySummaryStatus.pending),
        );
        expect(
          WeeklySummaryStatus.fromValue('generating'),
          equals(WeeklySummaryStatus.generating),
        );
        expect(
          WeeklySummaryStatus.fromValue('completed'),
          equals(WeeklySummaryStatus.completed),
        );
        expect(
          WeeklySummaryStatus.fromValue('failed'),
          equals(WeeklySummaryStatus.failed),
        );
      });

      test('should return pending for unknown value', () {
        expect(
          WeeklySummaryStatus.fromValue('unknown'),
          equals(WeeklySummaryStatus.pending),
        );
        expect(
          WeeklySummaryStatus.fromValue(''),
          equals(WeeklySummaryStatus.pending),
        );
        expect(
          WeeklySummaryStatus.fromValue('invalid'),
          equals(WeeklySummaryStatus.pending),
        );
      });
    });
  });

  group('WeeklySummaryModel', () {
    test('create should build valid model', () {
      final startDate = DateTime(2024, 1, 1);
      final endDate = DateTime(2024, 1, 7);

      final model = WeeklySummaryModel.create(
        weekStartDate: startDate,
        weekEndDate: endDate,
        content: 'Test content',
        articleCount: 5,
        diaryCount: 3,
      );

      expect(model.weekStartDate, equals(startDate));
      expect(model.weekEndDate, equals(endDate));
      expect(model.content, equals('Test content'));
      expect(model.articleCount, equals(5));
      expect(model.diaryCount, equals(3));
    });

    test('articleIdList should parse comma-separated IDs', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
        articleIds: '1,2,3,4,5',
      );

      expect(model.articleIdList, equals([1, 2, 3, 4, 5]));
    });

    test('articleIdList should filter invalid IDs', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
        articleIds: '1,abc,3,0,5',
      );

      expect(model.articleIdList, equals([1, 3, 5]));
    });

    test('articleIdList should return empty for null', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
      );

      expect(model.articleIdList, isEmpty);
    });

    test('diaryIdList should parse correctly', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
        diaryIds: '10,20,30',
      );

      expect(model.diaryIdList, equals([10, 20, 30]));
    });

    test('viewpointIdList should parse correctly', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
        viewpointIds: '1,2',
      );

      expect(model.viewpointIdList, equals([1, 2]));
    });

    test('isCompleted should be true when status is completed', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
      );
      model.status = WeeklySummaryStatus.completed;

      expect(model.isCompleted, isTrue);
      expect(model.isGenerating, isFalse);
      expect(model.isFailed, isFalse);
    });

    test('isGenerating should be true when status is generating', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
      );
      model.status = WeeklySummaryStatus.generating;

      expect(model.isGenerating, isTrue);
      expect(model.isCompleted, isFalse);
    });

    test('isFailed should be true when status is failed', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 1),
        weekEndDate: DateTime(2024, 1, 7),
      );
      model.status = WeeklySummaryStatus.failed;

      expect(model.isFailed, isTrue);
      expect(model.isCompleted, isFalse);
    });

    test('weekTitle should format correctly', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 15),
        weekEndDate: DateTime(2024, 1, 21),
      );

      expect(model.weekTitle, equals('1月15日 - 1月21日'));
    });

    test('weekLabel should include year and week number', () {
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 8),
        weekEndDate: DateTime(2024, 1, 14),
      );

      expect(model.weekLabel, contains('2024年第'));
      expect(model.weekLabel, contains('周'));
    });

    test('weekLabel should calculate correct week number', () {
      // For Jan 8, 2024:
      // daysDiff = 7, weekday = 1
      // weekNumber = ((7 + 1 - 1) / 7).ceil() = 1
      final model = WeeklySummaryModel.create(
        weekStartDate: DateTime(2024, 1, 8),
        weekEndDate: DateTime(2024, 1, 14),
      );

      expect(model.weekLabel, equals('2024年第1周'));
    });
  });
}
