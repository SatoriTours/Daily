import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/directory_config.dart';

void main() {
  group('DirectoryConfig', () {
    test('should have valid directory names', () {
      expect(DirectoryConfig.appDocuments.isNotEmpty, isTrue);
      expect(DirectoryConfig.backup.isNotEmpty, isTrue);
      expect(DirectoryConfig.cache.isNotEmpty, isTrue);
      expect(DirectoryConfig.images.isNotEmpty, isTrue);
      expect(DirectoryConfig.logs.isNotEmpty, isTrue);
    });

    test('should have correct app documents name', () {
      expect(DirectoryConfig.appDocuments, equals('DailySatori'));
    });

    test('should have lowercase directory names', () {
      expect(DirectoryConfig.backup, equals('backups'));
      expect(DirectoryConfig.cache, equals('cache'));
      expect(DirectoryConfig.images, equals('images'));
      expect(DirectoryConfig.logs, equals('logs'));
    });
  });
}
