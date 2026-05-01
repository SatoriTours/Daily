import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/providers/app_state_provider.dart';

void main() {
  group('AppStateModel', () {
    test('default values should be correct', () {
      final model = AppStateModel(lastActiveTime: DateTime.now());
      expect(model.currentNavIndex, equals(0));
      expect(model.isAppInBackground, isFalse);
      expect(model.isGlobalLoading, isFalse);
      expect(model.globalErrorMessage, equals(''));
      expect(model.globalSuccessMessage, equals(''));
      expect(model.globalInfoMessage, equals(''));
      expect(model.isSearchBarVisible, isFalse);
      expect(model.currentPage, equals(''));
    });

    test('copyWith should update only specified fields', () {
      final model = AppStateModel(lastActiveTime: DateTime.now());
      final updated = model.copyWith(currentNavIndex: 2);

      expect(updated.currentNavIndex, equals(2));
      expect(model.currentNavIndex, equals(0));
    });
  });
}
