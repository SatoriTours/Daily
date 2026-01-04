import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/providers/article_state_provider.dart';

void main() {
  group('ArticleUpdateEvent', () {
    test('none should create correct event', () {
      final event = const ArticleUpdateEvent.none();
      expect(event, isA<ArticleUpdateEventNone>());
    });

    test('deleted should contain articleId', () {
      final event = ArticleUpdateEvent.deleted(123);
      expect(event, isA<ArticleUpdateEventDeleted>());
    });
  });

  group('ArticleStateModel', () {
    test('default values should be correct', () {
      final model = ArticleStateModel();
      expect(model.articles, isEmpty);
      expect(model.isLoading, isFalse);
      expect(model.globalSearchQuery, equals(''));
      expect(model.isGlobalSearchActive, isFalse);
    });

    test('copyWith should update only specified fields', () {
      final model = ArticleStateModel();
      final updated = model.copyWith(isLoading: true);

      expect(updated.isLoading, isTrue);
      expect(model.isLoading, isFalse);
    });
  });
}
