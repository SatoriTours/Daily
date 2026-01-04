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

  group('FunctionAppService', () {
    test('should have correct serviceName', () {
      final service = FunctionAppService(
        serviceName: 'TestService',
        priority: ServicePriority.normal,
        onInit: () async {},
      );
      expect(service.serviceName, equals('TestService'));
    });

    test('should have correct priority', () {
      final service = FunctionAppService(
        serviceName: 'TestService',
        priority: ServicePriority.normal,
        onInit: () async {},
      );
      expect(service.priority, equals(ServicePriority.normal));
    });

    test('init should call onInit', () async {
      bool initialized = false;
      final service = FunctionAppService(
        serviceName: 'TestService',
        priority: ServicePriority.normal,
        onInit: () async {
          initialized = true;
        },
      );

      await service.init();
      expect(initialized, isTrue);
    });

    test('dispose without onDispose should not throw', () async {
      final service = FunctionAppService(
        serviceName: 'TestService',
        priority: ServicePriority.normal,
        onInit: () async {},
      );

      await service.dispose();
      expect(service.serviceName, equals('TestService'));
    });

    test('should handle async init', () async {
      int callCount = 0;
      final service = FunctionAppService(
        serviceName: 'TestService',
        priority: ServicePriority.normal,
        onInit: () async {
          await Future.delayed(const Duration(milliseconds: 10));
          callCount++;
        },
      );

      await service.init();
      expect(callCount, equals(1));
    });
  });
}
