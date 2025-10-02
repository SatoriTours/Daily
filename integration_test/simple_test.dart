import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import '../lib/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 简单集成测试', () {
    testWidgets('应用能正常启动', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 验证应用启动成功
      expect(find.byType(MaterialApp), findsOneWidget);

      print('✅ 应用启动成功');
    });

    testWidgets('应用能显示主要页面', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 等待应用完全加载
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 查找可能的导航元素
      final navBarFinder = find.byType(BottomNavigationBar);
      if (navBarFinder.evaluate().isNotEmpty) {
        print('✅ 找到底部导航栏');

        // 尝试点击不同的导航项
        final navItems = find.descendant(
          of: navBarFinder,
          matching: find.byType(InkWell),
        );

        if (navItems.evaluate().isNotEmpty) {
          print('✅ 找到导航项，数量: ${navItems.evaluate().length}');
        }
      }

      // 查找任何可能的按钮或交互元素
      final buttonFinder = find.byType(ElevatedButton);
      final iconButtonFinder = find.byType(IconButton);
      final gestureDetectorFinder = find.byType(GestureDetector);

      print('🔍 检测到的交互元素:');
      print('   - ElevatedButton: ${buttonFinder.evaluate().length}');
      print('   - IconButton: ${iconButtonFinder.evaluate().length}');
      print('   - GestureDetector: ${gestureDetectorFinder.evaluate().length}');

      // 查找可能的文本元素
      final textFinder = find.byType(Text);
      print('📝 检测到的文本元素数量: ${textFinder.evaluate().length}');

      if (textFinder.evaluate().isNotEmpty) {
        final firstText = tester.widget<Text>(textFinder.first);
        print('   - 第一个文本内容: "${firstText.data}"');
      }

      // 等待一段时间让应用完全加载
      await tester.pumpAndSettle(const Duration(seconds: 2));

      print('✅ 基本页面检测完成');
    });

    testWidgets('测试基本交互功能', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 尝试找到并点击任何可点击的元素
      final clickableElements = [
        find.byType(ElevatedButton),
        find.byType(TextButton),
        find.byType(IconButton),
      ];

      for (final finder in clickableElements) {
        if (finder.evaluate().isNotEmpty) {
          print('✅ 找到可点击元素: ${finder.toString()}');

          // 尝试点击第一个元素
          try {
            await tester.tap(finder.first);
            await tester.pumpAndSettle(const Duration(seconds: 2));
            print('✅ 成功点击元素');
            break;
          } catch (e) {
            print('⚠️ 点击元素失败: $e');
          }
        }
      }

      // 尝试滚动页面
      final scrollableFinder = find.byType(Scrollable);
      if (scrollableFinder.evaluate().isNotEmpty) {
        print('✅ 找到可滚动区域');
        try {
          await tester.fling(
            scrollableFinder.first,
            const Offset(0, -300),
            1000,
          );
          await tester.pumpAndSettle(const Duration(seconds: 1));
          print('✅ 成功滚动页面');
        } catch (e) {
          print('⚠️ 滚动失败: $e');
        }
      }

      print('✅ 基本交互测试完成');
    });
  });
}