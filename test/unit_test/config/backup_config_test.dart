import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/backup_config.dart';

void main() {
  group('BackupConfig', () {
    test('should have valid interval', () {
      expect(BackupConfig.interval.inHours, equals(6));
      expect(BackupConfig.productionIntervalHours, equals(6));
      expect(BackupConfig.developmentIntervalHours, equals(24));
    });

    test('should have correct file extension', () {
      expect(BackupConfig.fileExtension, equals('.zip'));
    });

    test('should have valid date format', () {
      expect(BackupConfig.dateFormat.isNotEmpty, isTrue);
      expect(BackupConfig.dateFormat.contains('yyyy'), isTrue);
    });

    test('interval should be positive', () {
      expect(BackupConfig.interval.inSeconds > 0, isTrue);
    });
  });
}
