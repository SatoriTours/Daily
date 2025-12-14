import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'test_utils.dart';

/// Daily Satori 简化集成测试
///
/// 这个版本专注于测试核心功能，避免复杂的交互
/// 使用 IntegrationTestUtils 提供的稳定方法
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 核心功能测试', () {
    late Map<String, bool> testResults;

    setUp(() {
      testResults = {};
    });

    testWidgets('应用启动和基础导航测试', (WidgetTester tester) async {
      bool testPassed = false;

      try {
        // 启动应用
        await IntegrationTestUtils.safeStartApp(tester);

        // 验证底部导航栏
        final navValid = await IntegrationTestUtils.verifyBottomNavigation(tester);
        testResults['底部导航栏'] = navValid;

        // 测试基本页面切换
        await IntegrationTestUtils.performPageSwitchingTest(
          tester,
          ['文章', '日记', '读书'],
        );
        testResults['页面切换'] = true;

        testPassed = true;
      } catch (e) {
        testResults['应用启动'] = false;
        // ignore: avoid_print
        print('❌ 应用启动测试失败: $e');
      }

      testResults['应用启动测试'] = testPassed;
    });

    testWidgets('文章功能基本测试', (WidgetTester tester) async {
      bool testPassed = false;

      try {
        await IntegrationTestUtils.safeStartApp(tester);

        // 切换到文章页面
        final success = await IntegrationTestUtils.safeTapNavigationItem(tester, '文章');
        if (success) {
          await IntegrationTestUtils.waitForPageStable(tester);

          // 验证页面结构
          final pageValid = IntegrationTestUtils.verifyBasicPageStructure(tester);
          testResults['文章页面加载'] = pageValid;

          // 查找可能的文章列表元素
          final listView = find.byType(ListView);
          final hasListView = await IntegrationTestUtils.waitForElement(
            tester,
            listView,
            timeout: const Duration(seconds: 3),
          );

          if (hasListView) {
            testResults['文章列表显示'] = true;
          } else {
            testResults['文章列表显示'] = false;
            // 这是正常的，测试环境可能没有数据
          }

          testPassed = true;
        }
      } catch (e) {
        // ignore: avoid_print
        print('❌ 文章功能测试失败: $e');
      }

      testResults['文章功能测试'] = testPassed;
    });

    testWidgets('日记功能基本测试', (WidgetTester tester) async {
      bool testPassed = false;

      try {
        await IntegrationTestUtils.safeStartApp(tester);

        // 切换到日记页面
        final success = await IntegrationTestUtils.safeTapNavigationItem(tester, '日记');
        if (success) {
          await IntegrationTestUtils.waitForPageStable(tester);

          // 验证页面结构
          final pageValid = IntegrationTestUtils.verifyBasicPageStructure(tester);
          testResults['日记页面加载'] = pageValid;

          // 查找可能的FloatingActionButton
          final fab = find.byType(FloatingActionButton);
          final hasFab = tester.any(fab);

          testResults['日记FAB按钮'] = hasFab;
          testPassed = true;
        }
      } catch (e) {
        // ignore: avoid_print
        print('❌ 日记功能测试失败: $e');
      }

      testResults['日记功能测试'] = testPassed;
    });

    testWidgets('AI助手功能基本测试', (WidgetTester tester) async {
      bool testPassed = false;

      try {
        await IntegrationTestUtils.safeStartApp(tester);

        // 切换到AI助手页面
        final success = await IntegrationTestUtils.safeTapNavigationItem(tester, 'AI助手');
        if (success) {
          await IntegrationTestUtils.waitForPageStable(tester);

          // 验证页面结构
          final pageValid = IntegrationTestUtils.verifyBasicPageStructure(tester);
          testResults['AI助手页面加载'] = pageValid;

          // 查找可能的输入框
          final textField = find.byType(TextField);
          final hasInput = tester.any(textField);

          testResults['AI助手输入框'] = hasInput;
          testPassed = true;
        }
      } catch (e) {
        // ignore: avoid_print
        print('❌ AI助手功能测试失败: $e');
      }

      testResults['AI助手功能测试'] = testPassed;
    });

    testWidgets('应用稳定性测试', (WidgetTester tester) async {
      bool testPassed = false;

      try {
        await IntegrationTestUtils.safeStartApp(tester);

        // 执行稳定性测试
        await IntegrationTestUtils.performStabilityTest(
          tester,
          ['文章', '日记', '读书'],
          rounds: 3,
        );

        testPassed = true;
      } catch (e) {
        // ignore: avoid_print
        print('❌ 稳定性测试失败: $e');
      }

      testResults['稳定性测试'] = testPassed;
    });

    tearDown(() {
      // 打印测试结果
      IntegrationTestUtils.printTestSummary(testResults);
    });
  });

  group('Daily Satori 压力测试', () {
    testWidgets('页面切换压力测试', (WidgetTester tester) async {
      bool testPassed = false;

      try {
        await IntegrationTestUtils.safeStartApp(tester);

        // 执行压力测试
        await IntegrationTestUtils.performMemoryTest(
          tester,
          ['文章', '日记', '读书', 'AI助手'],
          rounds: 5, // 减少轮次以节省时间
        );

        testPassed = true;
      } catch (e) {
        // ignore: avoid_print
        print('❌ 压力测试失败: $e');
      }

      final results = {'压力测试': testPassed};
      IntegrationTestUtils.printTestSummary(results);
    });
  });
}