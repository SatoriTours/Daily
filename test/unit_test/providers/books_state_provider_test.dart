import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/providers/books_state_provider.dart';

void main() {
  group('BooksStateModel', () {
    test('default values should be correct', () {
      final model = BooksStateModel();
      expect(model.viewpoints, isEmpty);
      expect(model.allBooks, isEmpty);
      expect(model.isLoading, isFalse);
      expect(model.currentViewpointIndex, equals(0));
      expect(model.filterBookID, equals(-1));
      expect(model.isProcessing, isFalse);
      expect(model.mode, equals(DisplayMode.allRandom));
    });

    test('copyWith should update only specified fields', () {
      final model = BooksStateModel();
      final updated = model.copyWith(isLoading: true);

      expect(updated.isLoading, isTrue);
      expect(model.isLoading, isFalse);
    });
  });

  group('DisplayMode', () {
    test('should have 2 values', () {
      expect(DisplayMode.values.length, equals(2));
    });

    test('allRandom should have correct name', () {
      expect(DisplayMode.allRandom.name, equals('allRandom'));
    });

    test('singleBook should have correct name', () {
      expect(DisplayMode.singleBook.name, equals('singleBook'));
    });
  });
}
