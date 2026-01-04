import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/utils/random_utils.dart';

void main() {
  group('RandomUtils', () {
    group('generateDeviceId', () {
      test('should return 10 uppercase letters', () {
        final result = RandomUtils.generateDeviceId();
        expect(result.length, equals(10));
        expect(result, matches(RegExp(r'^[A-Z]{10}$')));
      });

      test('should return consistent length for multiple calls', () {
        final result1 = RandomUtils.generateDeviceId();
        final result2 = RandomUtils.generateDeviceId();
        expect(result1.length, equals(result2.length));
      });
    });

    group('generateRandomPassword', () {
      test('should return password of specified length', () {
        expect(RandomUtils.generateRandomPassword(length: 8).length, equals(8));
        expect(RandomUtils.generateRandomPassword(length: 16).length, equals(16));
        expect(RandomUtils.generateRandomPassword(length: 32).length, equals(32));
      });

      test('should return non-empty string', () {
        final result = RandomUtils.generateRandomPassword(length: 12);
        expect(result.isNotEmpty, isTrue);
      });

      test('should use default length of 6', () {
        expect(RandomUtils.generateRandomPassword().length, equals(6));
      });
    });
  });
}
