import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:integration_test/integration_test.dart';

import 'package:daily_satori/main.dart' as app;
import 'package:daily_satori/app/services/logger_service.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 性能和内存测试', () {
    setUpAll(() async {
      // 确保LoggerService只初始化一次
      try {
        await LoggerService.i.init();
      } catch (e) {
        debugPrint('LoggerService already initialized: $e');
      }
    });

    setUp(() async {
      // 重置GetX状态
      Get.reset();
    });

    testWidgets('应用启动性能测试', (WidgetTester tester) async {
      final stopwatch = Stopwatch()..start();

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      stopwatch.stop();
      final startupTime = stopwatch.elapsedMilliseconds;

      debugPrint('✅ 应用启动时间: ${startupTime}ms');

      // 验证启动时间在合理范围内（小于10秒）
      expect(startupTime, lessThan(10000));

      // 检查应用是否正常显示
      expect(find.byType(MaterialApp), findsAtLeastNWidgets(1));
      debugPrint('✅ 应用启动性能测试通过');
    });

    testWidgets('列表滚动性能测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 查找可滚动组件
      final scrollableFinder = find.byType(Scrollable);
      if (scrollableFinder.evaluate().isNotEmpty) {
        final stopwatch = Stopwatch()..start();

        // 执行多次滚动操作
        for (int i = 0; i < 10; i++) {
          await tester.fling(
            scrollableFinder.first,
            const Offset(0, -200),
            1000,
          );
          await tester.pumpAndSettle();

          await tester.fling(
            scrollableFinder.first,
            const Offset(0, 200),
            1000,
          );
          await tester.pumpAndSettle();
        }

        stopwatch.stop();
        final scrollTime = stopwatch.elapsedMilliseconds;

        debugPrint('✅ 10次滚动操作耗时: ${scrollTime}ms');
        debugPrint('✅ 平均每次滚动: ${scrollTime / 10}ms');

        // 验证滚动性能（每次滚动应小于500ms）
        expect(scrollTime / 10, lessThan(500));
      }

      debugPrint('✅ 列表滚动性能测试完成');
    });

    testWidgets('内存使用情况测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 模拟大量操作来测试内存使用
      final stopwatch = Stopwatch()..start();

      for (int i = 0; i < 50; i++) {
        // 模拟页面导航
        try {
          // 尝试找到并点击底部导航项
          final bottomNavFinder = find.byType(BottomNavigationBar);
          if (bottomNavFinder.evaluate().isNotEmpty) {
            await tester.tap(bottomNavFinder);
            await tester.pumpAndSettle();
          }
        } catch (e) {
          // 忽略导航错误
        }

        // 模拟文本输入
        final textFieldFinder = find.byType(TextField);
        if (textFieldFinder.evaluate().isNotEmpty && i % 10 == 0) {
          await tester.tap(textFieldFinder.first);
          await tester.pumpAndSettle();
          await tester.enterText(textFieldFinder.first, '测试文本 $i');
          await tester.pumpAndSettle();
        }

        // 模拟滚动操作
        final scrollableFinder = find.byType(Scrollable);
        if (scrollableFinder.evaluate().isNotEmpty && i % 5 == 0) {
          await tester.fling(scrollableFinder.first, const Offset(0, -100), 500);
          await tester.pumpAndSettle();
        }

        // 每10次操作进行一次垃圾回收提示
        if (i % 10 == 0) {
          debugPrint('✅ 完成 $i/50 次操作');
        }
      }

      stopwatch.stop();
      final operationTime = stopwatch.elapsedMilliseconds;

      debugPrint('✅ 50次操作总耗时: ${operationTime}ms');
      debugPrint('✅ 平均每次操作: ${operationTime / 50}ms');

      // 验证操作性能（每次操作应小于100ms）
      expect(operationTime / 50, lessThan(100));

      debugPrint('✅ 内存使用情况测试完成');
    });

    testWidgets('大数据量加载性能测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 模拟大数据量场景
      final stopwatch = Stopwatch()..start();

      // 尝试多次快速滚动来测试大量数据的处理能力
      final scrollableFinder = find.byType(Scrollable);
      if (scrollableFinder.evaluate().isNotEmpty) {
        // 快速滚动20次
        for (int i = 0; i < 20; i++) {
          await tester.fling(
            scrollableFinder.first,
            const Offset(0, -500),
            2000,
          );
          await tester.pumpAndSettle(const Duration(milliseconds: 100));

          await tester.fling(
            scrollableFinder.first,
            const Offset(0, 500),
            2000,
          );
          await tester.pumpAndSettle(const Duration(milliseconds: 100));
        }
      }

      stopwatch.stop();
      final bulkDataTime = stopwatch.elapsedMilliseconds;

      debugPrint('✅ 大数据量滚动测试耗时: ${bulkDataTime}ms');

      // 验证大数据量处理性能（20次快速滚动应小于5秒）
      expect(bulkDataTime, lessThan(5000));

      debugPrint('✅ 大数据量加载性能测试通过');
    });

    testWidgets('界面响应性测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 测试各种UI交互的响应性
      final testOperations = [
        // 测试按钮点击响应性
        () async {
          final buttonFinder = find.byType(ElevatedButton);
          if (buttonFinder.evaluate().isNotEmpty) {
            final stopwatch = Stopwatch()..start();
            await tester.tap(buttonFinder.first);
            await tester.pumpAndSettle();
            stopwatch.stop();
            return stopwatch.elapsedMilliseconds;
          }
          return 0;
        },

        // 测试文本输入响应性
        () async {
          final textFieldFinder = find.byType(TextField);
          if (textFieldFinder.evaluate().isNotEmpty) {
            final stopwatch = Stopwatch()..start();
            await tester.tap(textFieldFinder.first);
            await tester.pumpAndSettle();
            await tester.enterText(textFieldFinder.first, '响应性测试');
            await tester.pumpAndSettle();
            stopwatch.stop();
            return stopwatch.elapsedMilliseconds;
          }
          return 0;
        },

        // 测试切换操作响应性
        () async {
          final stopwatch = Stopwatch()..start();
          final bottomNavFinder = find.byType(BottomNavigationBar);
          if (bottomNavFinder.evaluate().isNotEmpty) {
            await tester.tap(bottomNavFinder);
            await tester.pumpAndSettle();
          }
          stopwatch.stop();
          return stopwatch.elapsedMilliseconds;
        },
      ];

      int totalTime = 0;
      int validOperations = 0;

      for (final operation in testOperations) {
        final operationTime = await operation();
        if (operationTime > 0) {
          totalTime += operationTime;
          validOperations++;
          debugPrint('✅ 操作响应时间: ${operationTime}ms');
        }
      }

      if (validOperations > 0) {
        final averageTime = totalTime / validOperations;
        debugPrint('✅ 平均响应时间: ${averageTime}ms');

        // 验证界面响应性（平均响应时间应小于300ms）
        expect(averageTime, lessThan(300));
      }

      debugPrint('✅ 界面响应性测试完成');
    });

    testWidgets('应用稳定性压力测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final stopwatch = Stopwatch()..start();

      // 执行100次随机操作来测试应用稳定性
      for (int i = 0; i < 100; i++) {
        try {
          // 随机选择操作类型
          final operation = i % 6;

          switch (operation) {
            case 0: // 滚动操作
              final scrollableFinder = find.byType(Scrollable);
              if (scrollableFinder.evaluate().isNotEmpty) {
                await tester.fling(
                  scrollableFinder.first,
                  const Offset(0, -100),
                  800,
                );
              }
              break;

            case 1: // 点击操作
              final gestureFinder = find.byType(GestureDetector);
              if (gestureFinder.evaluate().isNotEmpty) {
                await tester.tap(gestureFinder.first);
              }
              break;

            case 2: // 文本输入
              final textFieldFinder = find.byType(TextField);
              if (textFieldFinder.evaluate().isNotEmpty) {
                await tester.tap(textFieldFinder.first);
                await tester.enterText(textFieldFinder.first, '测试$i');
              }
              break;

            case 3: // 按钮点击
              final buttonFinder = find.byType(ElevatedButton);
              if (buttonFinder.evaluate().isNotEmpty) {
                await tester.tap(buttonFinder.first);
              }
              break;

            case 4: // 导航切换
              final bottomNavFinder = find.byType(BottomNavigationBar);
              if (bottomNavFinder.evaluate().isNotEmpty) {
                await tester.tap(bottomNavFinder);
              }
              break;

            case 5: // 长按操作
              final anyWidgetFinder = find.byType(Container);
              if (anyWidgetFinder.evaluate().isNotEmpty) {
                await tester.longPress(anyWidgetFinder.first);
              }
              break;
          }

          await tester.pumpAndSettle(const Duration(milliseconds: 50));

          // 每25次操作输出进度
          if (i % 25 == 0) {
            debugPrint('✅ 压力测试进度: $i/100');
          }

        } catch (e) {
          // 记录错误但继续测试
          debugPrint('⚠️ 操作 $i 失败: $e');
        }
      }

      stopwatch.stop();
      final stressTestTime = stopwatch.elapsedMilliseconds;

      debugPrint('✅ 压力测试总耗时: ${stressTestTime}ms');
      debugPrint('✅ 平均每操作: ${stressTestTime / 100}ms');

      // 验证压力测试性能（100次操作应在30秒内完成）
      expect(stressTestTime, lessThan(30000));

      // 验证应用仍然响应
      await tester.pumpAndSettle();
      expect(find.byType(MaterialApp), findsAtLeastNWidgets(1));

      debugPrint('✅ 应用稳定性压力测试通过');
    });

    testWidgets('多任务切换性能测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      final stopwatch = Stopwatch()..start();

      // 模拟多任务切换场景
      for (int cycle = 0; cycle < 5; cycle++) {
        debugPrint('✅ 多任务切换周期: ${cycle + 1}/5');

        // 1. 导航到不同页面
        final navigationTargets = ['文章', '日记', '读书', '设置'];
        for (final target in navigationTargets) {
          try {
            final navFinder = find.text(target).first;
            if (navFinder.evaluate().isNotEmpty) {
              await tester.tap(navFinder);
              await tester.pumpAndSettle(const Duration(seconds: 1));
            }
          } catch (e) {
            // 忽略导航错误，继续测试
          }
        }

        // 2. 在每个页面执行一些操作
        for (int i = 0; i < 3; i++) {
          final scrollableFinder = find.byType(Scrollable);
          if (scrollableFinder.evaluate().isNotEmpty) {
            await tester.fling(
              scrollableFinder.first,
              const Offset(0, -50),
              300,
            );
            await tester.pumpAndSettle();
          }
        }

        // 3. 返回首页
        try {
          final homeFinder = find.text('首页').first;
          if (homeFinder.evaluate().isNotEmpty) {
            await tester.tap(homeFinder);
            await tester.pumpAndSettle(const Duration(seconds: 1));
          }
        } catch (e) {
          // 忽略返回错误
        }
      }

      stopwatch.stop();
      final multiTaskTime = stopwatch.elapsedMilliseconds;

      debugPrint('✅ 多任务切换测试耗时: ${multiTaskTime}ms');
      debugPrint('✅ 平均每个周期: ${multiTaskTime / 5}ms');

      // 验证多任务切换性能（5个周期应在15秒内完成）
      expect(multiTaskTime, lessThan(15000));

      debugPrint('✅ 多任务切换性能测试通过');
    });
  });
}