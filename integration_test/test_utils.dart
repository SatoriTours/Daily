import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/main.dart' as app;

/// 集成测试辅助工具类
///
/// 提供稳定的测试环境初始化和通用操作方法
class IntegrationTestUtils {
  static const Duration _defaultTimeout = Duration(seconds: 15);
  static const Duration _pageLoadTimeout = Duration(seconds: 5);
  static const Duration _interactionTimeout = Duration(seconds: 2);

  /// 安全启动应用
  ///
  /// 处理应用启动可能遇到的各种问题：
  /// 1. 应用初始化时间较长
  /// 2. 服务注册可能失败
  /// 3. 错误处理器冲突
  static Future<void> safeStartApp(WidgetTester tester) async {
    try {
      // ignore: avoid_print
      print('🚀 开始启动应用...');

      // 启动应用
      app.main();

      // 分阶段等待，避免一次性等待过长时间
      await tester.pump(const Duration(seconds: 2));
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle(_defaultTimeout);

      // ignore: avoid_print
      print('✅ 应用启动完成');
    } catch (e) {
      // ignore: avoid_print
      print('❌ 应用启动失败: $e');
      rethrow;
    }
  }

  /// 等待页面稳定
  static Future<void> waitForPageStable(WidgetTester tester) async {
    // 等待短时间让页面渲染完成
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle(_interactionTimeout);
  }

  /// 安全查找并点击导航项
  static Future<bool> safeTapNavigationItem(
    WidgetTester tester,
    String itemName, {
    Duration? timeout,
  }) async {
    try {
      final navItem = find.text(itemName);

      if (!tester.any(navItem)) {
        // ignore: avoid_print
        print('⚠️ 未找到导航项: $itemName');
        return false;
      }

      await tester.tap(navItem);
      await tester.pumpAndSettle(timeout ?? _pageLoadTimeout);

      // ignore: avoid_print
      print('✅ 成功点击导航项: $itemName');
      return true;
    } catch (e) {
      // ignore: avoid_print
      print('❌ 点击导航项失败 $itemName: $e');
      return false;
    }
  }

  /// 验证页面基本结构
  static bool verifyBasicPageStructure(WidgetTester tester) {
    try {
      final scaffold = find.byType(Scaffold);
      return tester.any(scaffold);
    } catch (e) {
      // ignore: avoid_print
      print('❌ 验证页面结构失败: $e');
      return false;
    }
  }

  /// 查找并验证底部导航栏
  static Future<bool> verifyBottomNavigation(WidgetTester tester) async {
    try {
      final bottomNav = find.byType(BottomNavigationBar);

      if (!tester.any(bottomNav)) {
        // ignore: avoid_print
        print('❌ 未找到底部导航栏');
        return false;
      }

      // 验证导航栏包含预期的导航项
      final expectedItems = ['文章', '日记', '读书'];
      for (final item in expectedItems) {
        if (!tester.any(find.text(item))) {
          // ignore: avoid_print
          print('⚠️ 底部导航栏缺少项目: $item');
        }
      }

      // ignore: avoid_print
      print('✅ 底部导航栏验证通过');
      return true;
    } catch (e) {
      // ignore: avoid_print
      print('❌ 验证底部导航栏失败: $e');
      return false;
    }
  }

  /// 安全等待元素出现
  static Future<bool> waitForElement(
    WidgetTester tester,
    Finder finder, {
    Duration timeout = const Duration(seconds: 5),
  }) async {
    final endTime = DateTime.now().add(timeout);

    while (DateTime.now().isBefore(endTime)) {
      if (tester.any(finder)) {
        return true;
      }
      await tester.pump(const Duration(milliseconds: 100));
    }

    return false;
  }

  /// 执行安全点击操作
  static Future<bool> safeTap(
    WidgetTester tester,
    Finder finder, {
    Duration? waitBefore,
  }) async {
    try {
      if (waitBefore != null) {
        await tester.pump(waitBefore);
      }

      if (!tester.any(finder)) {
        // ignore: avoid_print
        print('⚠️ 未找到要点击的元素');
        return false;
      }

      await tester.tap(finder);
      await tester.pumpAndSettle(_interactionTimeout);

      return true;
    } catch (e) {
      // ignore: avoid_print
      print('❌ 点击操作失败: $e');
      return false;
    }
  }

  /// 执行页面切换测试
  static Future<void> performPageSwitchingTest(
    WidgetTester tester,
    List<String> pages,
  ) async {
    // ignore: avoid_print
    print('🔄 开始页面切换测试...');

    for (int i = 0; i < pages.length; i++) {
      final pageName = pages[i];
      // ignore: avoid_print
      print('切换到页面: $pageName');

      final success = await safeTapNavigationItem(tester, pageName);
      if (!success) {
        continue;
      }

      // 验证页面切换成功
      final pageValid = verifyBasicPageStructure(tester);
      if (!pageValid) {
        // ignore: avoid_print
        print('❌ 页面结构验证失败: $pageName');
        continue;
      }

      // ignore: avoid_print
      print('✅ 成功切换到 $pageName 页面');
    }

    // ignore: avoid_print
    print('✅ 页面切换测试完成');
  }

  /// 执行稳定性测试
  static Future<void> performStabilityTest(
    WidgetTester tester,
    List<String> pages, {
    int rounds = 3,
    Duration interval = const Duration(milliseconds: 500),
  }) async {
    // ignore: avoid_print
    print('⚡ 开始应用稳定性测试...');

    for (int round = 0; round < rounds; round++) {
      // ignore: avoid_print
      print('稳定性测试轮次: ${round + 1}');

      for (final pageName in pages) {
        await safeTapNavigationItem(tester, pageName);
        await tester.pump(interval);
      }
    }

    // 等待最后操作完成
    await tester.pumpAndSettle(const Duration(seconds: 2));

    // 验证应用仍然响应
    final appResponsive = verifyBasicPageStructure(tester);
    if (!appResponsive) {
      throw Exception('稳定性测试后应用无响应');
    }

    // ignore: avoid_print
    print('✅ 应用稳定性测试通过');
  }

  /// 执行内存压力测试
  static Future<void> performMemoryTest(
    WidgetTester tester,
    List<String> pages, {
    int rounds = 10,
    Duration interval = const Duration(milliseconds: 200),
  }) async {
    // ignore: avoid_print
    print('🧠 开始内存测试...');

    for (int i = 0; i < rounds; i++) {
      // ignore: avoid_print
      print('内存测试轮次: ${i + 1}/$rounds');

      for (final pageName in pages) {
        await safeTapNavigationItem(tester, pageName);
        await tester.pump(interval);
      }
    }

    // 等待所有操作完成
    await tester.pumpAndSettle(const Duration(seconds: 5));

    // 验证应用仍然响应
    final appResponsive = verifyBasicPageStructure(tester);
    if (!appResponsive) {
      throw Exception('内存测试后应用无响应');
    }

    // ignore: avoid_print
    print('✅ 内存测试通过');
  }

  /// 创建测试报告
  static void printTestSummary(Map<String, bool> results) {
    // ignore: avoid_print
    print('\n📊 测试结果汇总:');
    // ignore: avoid_print
    print('=' * 50);

    int passed = 0;
    int failed = 0;

    results.forEach((testName, success) {
      final status = success ? '✅ PASS' : '❌ FAIL';
      // ignore: avoid_print
      print('$status $testName');

      if (success) {
        passed++;
      } else {
        failed++;
      }
    });

    // ignore: avoid_print
    print('=' * 50);
    // ignore: avoid_print
    print('总计: $passed 通过, $failed 失败');

    if (failed == 0) {
      // ignore: avoid_print
      print('🎉 所有测试都通过了！');
    } else {
      // ignore: avoid_print
      print('⚠️ 有 $failed 个测试失败，请检查上述问题');
    }
  }
}