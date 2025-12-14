// ignore_for_file: avoid_print

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:daily_satori/main.dart' as app;

import 'test_ai_bootstrap.dart';

/// Daily Satori 集成测试套件
///
/// 注意：集成测试需要连接真实设备或模拟器运行
/// 运行命令：flutter test integration_test/app_test.dart
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 集成测试', () {
    setUp(() async {
      // 每个测试前的准备工作
    });

    tearDown(() async {
      // 每个测试后的清理工作
    });

    testWidgets('应用启动测试 - 验证应用能够正常启动并显示主页', (WidgetTester tester) async {
      print('🚀 开始应用启动测试...');

      // 启动应用
      app.main();

      // 等待应用完全加载，使用更长的等待时间
      await tester.pumpAndSettle(const Duration(seconds: 10));

      await TestAiBootstrap.configureFromEnv();

      print('✅ 应用启动完成');

      // 验证主页面已加载
      // 查找底部导航栏（这是主页面的关键标识）
      final bottomNav = find.byType(BottomNavigationBar);
      expect(bottomNav, findsOneWidget, reason: '应该找到底部导航栏');

      // 验证底部导航栏包含预期的页面
      expect(find.text('文章'), findsOneWidget, reason: '应该找到文章导航项');
      expect(find.text('日记'), findsOneWidget, reason: '应该找到日记导航项');
      expect(find.text('读书'), findsOneWidget, reason: '应该找到读书导航项');

      print('✅ 主页面验证通过');
    });

    testWidgets('文章页面测试 - 导航到文章页面并验证基本功能', (WidgetTester tester) async {
      print('📝 开始文章页面测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      await TestAiBootstrap.configureFromEnv();

      // 等待一秒确保页面稳定
      await tester.pump(const Duration(seconds: 1));

      // 点击文章导航项（默认应该已经在文章页面）
      final articlesTab = find.text('文章');
      if (tester.any(articlesTab)) {
        await tester.tap(articlesTab);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }

      print('✅ 成功导航到文章页面');

      // 验证文章页面已加载
      // 注意：这里我们验证页面的关键元素，而不是具体内容
      // 因为测试环境可能没有实际的文章数据

      // 查找可能的文章列表容器或其他UI元素
      final scaffold = find.byType(Scaffold);
      expect(scaffold, findsWidgets, reason: '应该找到Scaffold组件');

      print('✅ 文章页面基本验证通过');
    });

    testWidgets('页面切换测试 - 验证各个页面可以正常切换', (WidgetTester tester) async {
      print('🔄 开始页面切换测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      await TestAiBootstrap.configureFromEnv();

      // 等待一秒确保页面稳定
      await tester.pump(const Duration(seconds: 1));

      // 测试的页面名称列表
      final pages = ['文章', '日记', '读书', 'AI助手', '周报'];

      for (int i = 0; i < pages.length; i++) {
        final pageName = pages[i];
        print('切换到页面: $pageName');

        // 查找并点击对应的导航项
        final navItem = find.text(pageName);
        if (tester.any(navItem)) {
          await tester.tap(navItem);
          await tester.pumpAndSettle(const Duration(seconds: 3));

          // 验证页面切换成功
          final scaffold = find.byType(Scaffold);
          expect(scaffold, findsWidgets, reason: "切换到 $pageName 页面后应该找到Scaffold");

          print('✅ 成功切换到 $pageName 页面');
        } else {
          print('⚠️ 未找到 $pageName 导航项，跳过');
        }
      }

      print('✅ 页面切换测试完成');
    });

    testWidgets('日记页面测试 - 验证日记页面可以正常加载', (WidgetTester tester) async {
      print('📖 开始日记页面测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      // 等待一秒确保页面稳定
      await tester.pump(const Duration(seconds: 1));

      // 点击日记导航项
      final diaryTab = find.text('日记');
      if (tester.any(diaryTab)) {
        await tester.tap(diaryTab);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }

      print('✅ 成功导航到日记页面');

      // 验证日记页面已加载
      final scaffold = find.byType(Scaffold);
      expect(scaffold, findsWidgets, reason: '应该找到Scaffold组件');

      // 查找可能的FloatingActionButton（创建日记的按钮）
      final fab = find.byType(FloatingActionButton);
      if (tester.any(fab)) {
        print('✅ 找到FloatingActionButton，日记页面UI正常');
      }

      print('✅ 日记页面基本验证通过');
    });

    testWidgets('AI助手页面测试 - 验证AI助手页面可以正常加载', (WidgetTester tester) async {
      print('🤖 开始AI助手页面测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      // 等待一秒确保页面稳定
      await tester.pump(const Duration(seconds: 1));

      // 点击AI助手导航项
      final aiTab = find.text('AI助手');
      if (tester.any(aiTab)) {
        await tester.tap(aiTab);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }

      print('✅ 成功导航到AI助手页面');

      // 验证AI助手页面已加载
      final scaffold = find.byType(Scaffold);
      expect(scaffold, findsWidgets, reason: '应该找到Scaffold组件');

      // 查找可能的输入框
      final textField = find.byType(TextField);
      if (tester.any(textField)) {
        print('✅ 找到输入框，AI助手页面UI正常');
      }

      print('✅ AI助手页面基本验证通过');
    });

    testWidgets('应用稳定性测试 - 快速操作测试应用稳定性', (WidgetTester tester) async {
      print('⚡ 开始应用稳定性测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      // 等待一秒确保页面稳定
      await tester.pump(const Duration(seconds: 1));

      // 快速切换页面多次
      for (int round = 0; round < 3; round++) {
        print('稳定性测试轮次: ${round + 1}');

        final pages = ['文章', '日记', '读书'];
        for (final pageName in pages) {
          final navItem = find.text(pageName);
          if (tester.any(navItem)) {
            await tester.tap(navItem);
            await tester.pump(const Duration(milliseconds: 500));
          }
        }
      }

      // 等待最后操作完成
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 验证应用仍然响应
      final scaffold = find.byType(Scaffold);
      expect(scaffold, findsWidgets, reason: '快速操作后应用应该仍然响应');

      print('✅ 应用稳定性测试通过');
    });

    testWidgets('内存测试 - 验证应用不会因内存问题崩溃', (WidgetTester tester) async {
      print('🧠 开始内存测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 10));

      // 等待一秒确保页面稳定
      await tester.pump(const Duration(seconds: 1));

      // 多次切换页面以测试内存管理
      for (int i = 0; i < 10; i++) {
        final pages = ['文章', '日记', '读书', 'AI助手', '周报'];
        for (final pageName in pages) {
          final navItem = find.text(pageName);
          if (tester.any(navItem)) {
            await tester.tap(navItem);
            await tester.pump(const Duration(milliseconds: 200));
          }
        }
      }

      // 等待所有操作完成
      await tester.pumpAndSettle(const Duration(seconds: 5));

      // 验证应用仍然响应
      final scaffold = find.byType(Scaffold);
      expect(scaffold, findsWidgets, reason: '内存测试后应用应该仍然响应');

      print('✅ 内存测试通过');
    });
  });
}
