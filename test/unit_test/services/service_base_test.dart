import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/services/service_base.dart';

void main() {
  group('ServicePriority', () {
    test('should have 4 priority values', () {
      expect(ServicePriority.values.length, equals(4));
    });

    test('critical should have correct order', () {
      expect(ServicePriority.critical.index, equals(0));
    });

    test('high should have correct order', () {
      expect(ServicePriority.high.index, equals(1));
    });

    test('normal should have correct order', () {
      expect(ServicePriority.normal.index, equals(2));
    });

    test('low should have correct order', () {
      expect(ServicePriority.low.index, equals(3));
    });
  });

  group('AppService', () {
    test(
      'should have default serviceName from class name (removes Service suffix)',
      () {
        final service = TestService();
        // 去掉 Service 后缀，TestService -> Test
        expect(service.serviceName, equals('Test'));
      },
    );

    test('should have default priority normal', () {
      final service = TestService();
      expect(service.priority, equals(ServicePriority.normal));
    });

    test('should support custom priority', () {
      final service = CustomPriorityService();
      expect(service.priority, equals(ServicePriority.critical));
    });

    test('should support custom serviceName', () {
      final service = CustomNameService();
      expect(service.serviceName, equals('CustomName'));
    });

    test('init should complete successfully', () async {
      final service = TestService();
      await expectLater(service.init(), completes);
    });

    test('dispose without override should not throw', () async {
      final service = TestService();
      // dispose 返回 void，直接调用不应抛出异常
      expect(() => service.dispose(), returnsNormally);
    });
  });
}

class TestService extends AppService {
  @override
  Future<void> init() async {}
}

class CustomPriorityService extends AppService {
  @override
  ServicePriority get priority => ServicePriority.critical;

  @override
  Future<void> init() async {}
}

class CustomNameService extends AppService {
  @override
  String get serviceName => 'CustomName';

  @override
  Future<void> init() async {}
}
