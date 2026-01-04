import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/config/session_config.dart';

void main() {
  group('SessionConfig', () {
    test('should have positive expire time', () {
      expect(SessionConfig.expireTime.inMinutes > 0, isTrue);
    });

    test('should have positive inactivity timeout', () {
      expect(SessionConfig.inactivityTimeout.inMinutes > 0, isTrue);
    });

    test('should have positive check interval', () {
      expect(SessionConfig.checkInterval.inMinutes > 0, isTrue);
    });

    test('expire time should be reasonable', () {
      expect(SessionConfig.expireTime.inMinutes, equals(30));
    });

    test('inactivity timeout should be less than expire time', () {
      expect(SessionConfig.inactivityTimeout < SessionConfig.expireTime, isTrue);
    });

    test('check interval should be less than expire time', () {
      expect(SessionConfig.checkInterval < SessionConfig.expireTime, isTrue);
    });

    test('should have reasonable values', () {
      expect(SessionConfig.inactivityTimeout.inMinutes, equals(1));
      expect(SessionConfig.inactivityTimeout.inSeconds, equals(90));
      expect(SessionConfig.checkInterval.inMinutes, equals(15));
    });
  });
}
