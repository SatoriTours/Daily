import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/utils/string_utils.dart';

void main() {
  group('StringUtils', () {
    group('isChinese', () {
      test('should return true for Chinese text', () {
        expect(StringUtils.isChinese('你好'), isTrue);
        expect(StringUtils.isChinese('测试中文'), isTrue);
      });

      test('should return false for non-Chinese text', () {
        expect(StringUtils.isChinese('hello'), isFalse);
        expect(StringUtils.isChinese('12345'), isFalse);
      });

      test('should return false for empty string', () {
        expect(StringUtils.isChinese(''), isFalse);
      });
    });

    group('getSubstring', () {
      test('should return full string if shorter than length', () {
        expect(StringUtils.getSubstring('hi', length: 10), 'hi');
      });

      test('should truncate string to specified length', () {
        expect(StringUtils.getSubstring('hello world', length: 5), 'hello');
      });

      test('should add suffix when truncating', () {
        expect(
          StringUtils.getSubstring('hello world', length: 5, suffix: '...'),
          'hello...',
        );
      });

      test('should throw error for negative length', () {
        expect(
          () => StringUtils.getSubstring('test', length: -1),
          throwsArgumentError,
        );
      });
    });

    group('firstLine', () {
      test('should return first line of multiline text', () {
        expect(StringUtils.firstLine('line1\nline2\nline3'), 'line1');
      });

      test('should return original string if no newline', () {
        expect(StringUtils.firstLine('single line'), 'single line');
      });

      test('should handle empty string', () {
        expect(StringUtils.firstLine(''), '');
      });
    });

    group('singleLine', () {
      test('should convert multiline to single line', () {
        expect(StringUtils.singleLine('line1\nline2'), 'line1 line2');
      });

      test('should handle null', () {
        expect(StringUtils.singleLine(null), '');
      });
    });

    group('getTopLevelDomain', () {
      test('should extract domain from URL', () {
        expect(
          StringUtils.getTopLevelDomain('https://www.example.com'),
          'example.com',
        );
        expect(StringUtils.getTopLevelDomain('www.example.com'), 'example.com');
        expect(StringUtils.getTopLevelDomain('example.org'), 'example.org');
      });

      test('should extract TLD for two-part TLDs', () {
        expect(StringUtils.getTopLevelDomain('www.google.co.uk'), 'co.uk');
        expect(StringUtils.getTopLevelDomain('google.co.uk'), 'co.uk');
      });

      test('should return empty for null', () {
        expect(StringUtils.getTopLevelDomain(null), '');
      });

      test('should return host for single-part domains', () {
        expect(StringUtils.getTopLevelDomain('localhost'), 'localhost');
      });
    });

    group('getUrlFromText', () {
      test('should extract URL from text', () {
        expect(
          StringUtils.getUrlFromText('Visit https://www.example.com'),
          'https://www.example.com',
        );
      });

      test('should return empty string if no URL found', () {
        expect(StringUtils.getUrlFromText('No URL here'), '');
      });

      test('should handle empty text', () {
        expect(StringUtils.getUrlFromText(''), '');
      });
    });
  });
}
