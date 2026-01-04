import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/ai_config.dart';
import 'package:daily_satori/app/config/database_config.dart';
import 'package:daily_satori/app/config/network_config.dart';
import 'package:daily_satori/app/config/pagination_config.dart';
import 'package:daily_satori/app/config/regex_config.dart';

void main() {
  group('AIConfig', () {
    test('should have correct default values', () {
      expect(AIConfig.timeout.inSeconds, equals(30));
      expect(AIConfig.maxSummaryLength, equals(500));
      expect(AIConfig.maxContentLength, equals(10000));
      expect(AIConfig.maxTitleLength, equals(100));
      expect(AIConfig.maxTagsPerArticle, equals(10));
      expect(AIConfig.defaultTemperature, equals(0.5));
      expect(AIConfig.maxProcessContentLength, equals(50000));
      expect(AIConfig.minHtmlLength, equals(50));
      expect(AIConfig.minTextLength, equals(20));
      expect(AIConfig.longTitleThreshold, equals(50));
      expect(AIConfig.randomRecommendationCount, equals(10));
    });

    test('timeout should be positive', () {
      expect(AIConfig.timeout.inSeconds > 0, isTrue);
    });

    test('max values should be greater than min values', () {
      expect(AIConfig.maxContentLength > AIConfig.maxSummaryLength, isTrue);
      expect(AIConfig.maxProcessContentLength > AIConfig.maxContentLength, isTrue);
    });
  });

  group('DatabaseConfig', () {
    test('should have correct version', () {
      expect(DatabaseConfig.version, equals(1));
    });

    test('should have database name', () {
      expect(DatabaseConfig.name.isNotEmpty, isTrue);
    });

    test('should have positive max size', () {
      expect(DatabaseConfig.maxSize > 0, isTrue);
      expect(DatabaseConfig.maxSize > 1024 * 1024, isTrue);
    });

    test('should have objectBox directory', () {
      expect(DatabaseConfig.objectBoxDir.isNotEmpty, isTrue);
    });
  });

  group('NetworkConfig', () {
    test('should have positive timeout', () {
      expect(NetworkConfig.timeout.inSeconds > 0, isTrue);
    });

    test('should have positive max retries', () {
      expect(NetworkConfig.maxRetries >= 0, isTrue);
    });

    test('should have positive retry delay', () {
      expect(NetworkConfig.retryDelay.inSeconds > 0, isTrue);
    });
  });

  group('PaginationConfig', () {
    test('should have valid page sizes', () {
      expect(PaginationConfig.defaultPageSize > 0, isTrue);
      expect(PaginationConfig.maxPageSize > 0, isTrue);
      expect(PaginationConfig.minPageSize > 0, isTrue);
      expect(PaginationConfig.defaultPageSize <= PaginationConfig.maxPageSize, isTrue);
      expect(PaginationConfig.minPageSize <= PaginationConfig.defaultPageSize, isTrue);
    });
  });

  group('RegexConfig', () {
    test('URL regex should match valid URLs', () {
      expect(RegexConfig.url.hasMatch('https://example.com'), isTrue);
      expect(RegexConfig.url.hasMatch('http://test.org'), isTrue);
    });

    test('Email regex should match valid emails', () {
      expect(RegexConfig.email.hasMatch('test@example.com'), isTrue);
      expect(RegexConfig.email.hasMatch('user.name@domain.org'), isTrue);
    });

    test('Phone regex should match valid phone numbers', () {
      expect(RegexConfig.phone.hasMatch('+1234567890'), isTrue);
      expect(RegexConfig.phone.hasMatch('123-456-7890'), isTrue);
    });
  });
}
