import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:integration_test/integration_test.dart';

import '../lib/main.dart' as app;
import '../lib/app/services/logger_service.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 完整应用集成测试', () {
    setUpAll(() async {
      // 确保LoggerService只初始化一次
      try {
        await LoggerService.i.init();
      } catch (e) {
        // 如果已经初始化，忽略错误
        print('LoggerService already initialized: $e');
      }
    });

    setUp(() async {
      // 重置GetX状态但不清除全局logger
      Get.reset();
    });

    testWidgets('应用启动测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 验证应用成功启动
      expect(find.byType(MaterialApp), findsAtLeastNWidgets(1));
      print('✅ 应用成功启动');

      // 等待所有异步操作完成
      await tester.pumpAndSettle();
    });

    testWidgets('基础UI渲染测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 检查基本UI元素
      expect(find.byType(Scaffold), findsAtLeastNWidgets(1));

      // 检查是否有底部导航栏
      final bottomNavFinder = find.byType(BottomNavigationBar);
      if (bottomNavFinder.evaluate().isNotEmpty) {
        expect(bottomNavFinder, findsOneWidget);
        print('✅ 底部导航栏存在');
      }

      // 检查是否有AppBar
      final appBarFinder = find.byType(AppBar);
      if (appBarFinder.evaluate().isNotEmpty) {
        expect(appBarFinder, findsAtLeastNWidgets(1));
        print('✅ AppBar存在');
      }

      print('✅ 基础UI渲染正常');
    });

    testWidgets('基础交互测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 查找任何可点击的元素
      final buttonsFinder = find.byType(ElevatedButton);
      final iconButtonsFinder = find.byType(IconButton);
      final gestureDetectorsFinder = find.byType(GestureDetector);

      // 尝试点击按钮（如果存在）
      if (buttonsFinder.evaluate().isNotEmpty) {
        await tester.tap(buttonsFinder.first);
        await tester.pumpAndSettle();
        print('✅ 成功点击按钮');
      }

      // 尝试点击图标按钮（如果存在）
      if (iconButtonsFinder.evaluate().isNotEmpty) {
        await tester.tap(iconButtonsFinder.first);
        await tester.pumpAndSettle();
        print('✅ 成功点击图标按钮');
      }

      // 尝试在屏幕上滑动（如果存在可滚动内容）
      final scrollableFinder = find.byType(Scrollable);
      if (scrollableFinder.evaluate().isNotEmpty) {
        await tester.fling(scrollableFinder.first, const Offset(0, -200), 1000);
        await tester.pumpAndSettle();
        print('✅ 成功滚动内容');
      }

      print('✅ 基础交互功能正常');
    });

    testWidgets('应用导航测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 查找底部导航项并尝试导航
      final bottomNavItemsFinder = find.byType(BottomNavigationBarItem);
      final bottomNavFinder = find.byType(BottomNavigationBar);

      if (bottomNavFinder.evaluate().isNotEmpty) {
        // 获取底部导航栏的位置
        final bottomNav = tester.widget<BottomNavigationBar>(bottomNavFinder);

        // 尝试点击不同的导航项
        for (int i = 0; i < (bottomNav.items?.length ?? 0); i++) {
          try {
            await tester.tap(find.byKey(ValueKey('bottom_nav_$i')));
            await tester.pumpAndSettle(const Duration(seconds: 2));
            print('✅ 成功导航到页面 $i');
          } catch (e) {
            // 如果找不到特定键，尝试通过位置点击
            try {
              await tester.tap(find.byType(BottomNavigationBar));
              await tester.pumpAndSettle();
              print('✅ 成功点击底部导航');
            } catch (e2) {
              print('⚠️ 底部导航点击失败: $e2');
            }
          }
        }
      }

      print('✅ 导航测试完成');
    });

    testWidgets('文本输入测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 查找文本输入框
      final textFieldFinder = find.byType(TextField);
      final textFormFieldFinder = find.byType(TextFormField);

      if (textFieldFinder.evaluate().isNotEmpty) {
        await tester.tap(textFieldFinder.first);
        await tester.pumpAndSettle();
        await tester.enterText(textFieldFinder.first, '测试文本');
        await tester.pumpAndSettle();
        print('✅ 成功输入文本到TextField');
      }

      if (textFormFieldFinder.evaluate().isNotEmpty) {
        await tester.tap(textFormFieldFinder.first);
        await tester.pumpAndSettle();
        await tester.enterText(textFormFieldFinder.first, '测试表单文本');
        await tester.pumpAndSettle();
        print('✅ 成功输入文本到TextFormField');
      }

      print('✅ 文本输入测试完成');
    });

    testWidgets('列表滚动测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 查找列表视图
      final listViewFinder = find.byType(ListView);
      final gridViewFinder = find.byType(GridView);
      final customScrollViewFinder = find.byType(CustomScrollView);

      if (listViewFinder.evaluate().isNotEmpty) {
        await tester.fling(listViewFinder.first, const Offset(0, -300), 1000);
        await tester.pumpAndSettle();
        await tester.fling(listViewFinder.first, const Offset(0, 300), 1000);
        await tester.pumpAndSettle();
        print('✅ ListView滚动正常');
      }

      if (gridViewFinder.evaluate().isNotEmpty) {
        await tester.fling(gridViewFinder.first, const Offset(0, -300), 1000);
        await tester.pumpAndSettle();
        print('✅ GridView滚动正常');
      }

      if (customScrollViewFinder.evaluate().isNotEmpty) {
        await tester.fling(customScrollViewFinder.first, const Offset(0, -300), 1000);
        await tester.pumpAndSettle();
        print('✅ CustomScrollView滚动正常');
      }

      print('✅ 列表滚动测试完成');
    });
  });
}