import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/providers/diary_state_provider.dart';

void main() {
  group('DiaryStateModel', () {
    test('default values should be correct', () {
      final model = DiaryStateModel();
      expect(model.diaries, isEmpty);
      expect(model.isLoading, isFalse);
      expect(model.activeDiaryId, equals(-1));
      expect(model.globalTagFilter, equals(''));
    });

    test('copyWith should update only specified fields', () {
      final model = DiaryStateModel();
      final updated = model.copyWith(isLoading: true);

      expect(updated.isLoading, isTrue);
      expect(model.isLoading, isFalse);
    });
  });
}
