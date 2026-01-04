import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/data/article/article_status.dart';

void main() {
  group('ArticleStatus', () {
    test('should have 4 status values', () {
      expect(ArticleStatus.values.length, equals(4));
    });

    test('pending should have correct value', () {
      expect(ArticleStatus.pending.value, equals('pending'));
      expect(ArticleStatus.pending.label, equals('待处理'));
    });

    test('webContentFetched should have correct value', () {
      expect(ArticleStatus.webContentFetched.value, equals('web_content_fetched'));
      expect(ArticleStatus.webContentFetched.label, equals('网页内容已获取'));
    });

    test('completed should have correct value', () {
      expect(ArticleStatus.completed.value, equals('completed'));
      expect(ArticleStatus.completed.label, equals('已完成'));
    });

    test('error should have correct value', () {
      expect(ArticleStatus.error.value, equals('error'));
      expect(ArticleStatus.error.label, equals('错误'));
    });

    group('fromValue', () {
      test('should return correct status for valid values', () {
        expect(ArticleStatus.fromValue('pending'), equals(ArticleStatus.pending));
        expect(ArticleStatus.fromValue('web_content_fetched'), equals(ArticleStatus.webContentFetched));
        expect(ArticleStatus.fromValue('completed'), equals(ArticleStatus.completed));
        expect(ArticleStatus.fromValue('error'), equals(ArticleStatus.error));
      });

      test('should return error for unknown value', () {
        expect(ArticleStatus.fromValue('unknown'), equals(ArticleStatus.error));
        expect(ArticleStatus.fromValue(''), equals(ArticleStatus.error));
        expect(ArticleStatus.fromValue('invalid'), equals(ArticleStatus.error));
      });
    });

    group('toString', () {
      test('should return value as string', () {
        expect(ArticleStatus.pending.toString(), equals('pending'));
        expect(ArticleStatus.completed.toString(), equals('completed'));
        expect(ArticleStatus.error.toString(), equals('error'));
      });
    });
  });
}
