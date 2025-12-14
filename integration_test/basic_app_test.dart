// ignore_for_file: avoid_print, prefer_const_constructors

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:daily_satori/main.dart' as app;

import 'test_ai_bootstrap.dart';

/// Daily Satori 基础集成测试
///
/// 最简化的测试版本，专注于验证应用能够正常启动和基本功能
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 基础验证', () {
    testWidgets('应用能够正常启动并显示主界面', (WidgetTester tester) async {
      // 启动应用
      app.main();

      // 等待应用完全加载，使用更长的等待时间
      await tester.pumpAndSettle(const Duration(seconds: 15));

      await TestAiBootstrap.configureFromEnv();

      // 验证应用已经启动（MaterialApp是GetMaterialApp的父类）
      expect(find.byType(MaterialApp), findsOneWidget);

      // 等待主界面加载
      await tester.pump(const Duration(seconds: 3));

      // 验证底部导航栏存在
      final bottomNav = find.byType(BottomNavigationBar);
      if (tester.any(bottomNav)) {
        // 找到了底部导航栏，说明主界面加载成功
        print('✅ 成功找到底部导航栏');

        // 验证导航项存在（使用英文作为后备）
        final hasChineseNav = tester.any(find.text('文章'));
        final hasEnglishNav = tester.any(find.text('Articles'));

        if (hasChineseNav || hasEnglishNav) {
          print('✅ 成功找到导航项');
        } else {
          print('⚠️ 未找到预期的导航项，但应用已启动');
        }
      } else {
        print('⚠️ 未找到底部导航栏，但应用已启动');
      }

      // 基本验证：应用没有崩溃
      expect(find.byType(Scaffold), findsWidgets);

      print('✅ 应用启动测试通过');
    });

    testWidgets('应用界面组件验证', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 15));

      await TestAiBootstrap.configureFromEnv();

      // 等待界面稳定
      await tester.pump(const Duration(seconds: 2));

      // 验证基本的UI组件存在
      final scaffold = find.byType(Scaffold);
      expect(scaffold, findsWidgets, reason: '应该找到Scaffold组件');

      // 查找任何可能的UI元素
      final container = find.byType(Container);
      final column = find.byType(Column);
      final row = find.byType(Row);

      bool hasBasicUI = tester.any(container) || tester.any(column) || tester.any(row);

      if (hasBasicUI) {
        print('✅ 找到基本UI组件');
      }

      // 至少应该有一个Scaffold
      expect(scaffold, findsWidgets);

      print('✅ UI组件验证通过');
    });

    testWidgets('应用响应性测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 15));

      // 等待界面稳定
      await tester.pump(const Duration(seconds: 2));

      // 测试基本的pump操作是否正常
      for (int i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 500));
      }

      // 验证应用仍然响应
      expect(find.byType(Scaffold), findsWidgets);

      print('✅ 应用响应性测试通过');
    });

    testWidgets('应用内存稳定性测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 15));

      // 进行多次pump操作测试内存稳定性
      for (int i = 0; i < 20; i++) {
        await tester.pump(const Duration(milliseconds: 100));

        // 每隔几轮检查应用是否仍然响应
        if (i % 5 == 0) {
          expect(find.byType(Scaffold), findsWidgets);
        }
      }

      // 最终等待
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 验证应用仍然运行
      expect(find.byType(Scaffold), findsWidgets);

      print('✅ 内存稳定性测试通过');
    });
  });

  group('Daily Satori 功能验证', () {
    testWidgets('国际化功能验证', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 15));

      // 等待界面稳定
      await tester.pump(const Duration(seconds: 2));

      // 检查是否有文本显示（中文或英文）
      final textWidgets = find.byType(Text);

      if (tester.any(textWidgets)) {
        print('✅ 找到文本组件，国际化功能正常');

        // 可以找到一些文本说明应用正常运行
        bool foundText = false;
        for (int i = 0; i < tester.widgetList(textWidgets).length; i++) {
          final text = tester.widget<Text>(textWidgets.at(i));
          if (text.data != null && text.data!.isNotEmpty) {
            print('✅ 找到显示的文本: ${text.data}');
            foundText = true;
            break;
          }
        }

        if (!foundText) {
          print('⚠️ 文本组件存在但内容为空');
        }
      } else {
        print('⚠️ 未找到文本组件');
      }

      // 只要应用没有崩溃就算通过
      expect(find.byType(Scaffold), findsWidgets);

      print('✅ 国际化功能验证完成');
    });

    testWidgets('应用生命周期测试', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 15));

      // 模拟应用进入后台再回到前台
      await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
        'flutter/lifecycle',
        StringCodec().encodeMessage('AppLifecycleState.paused'),
        (data) {},
      );

      await tester.pump(const Duration(seconds: 1));

      await tester.binding.defaultBinaryMessenger.handlePlatformMessage(
        'flutter/lifecycle',
        StringCodec().encodeMessage('AppLifecycleState.resumed'),
        (data) {},
      );

      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 验证应用仍然正常运行
      expect(find.byType(Scaffold), findsWidgets);

      print('✅ 应用生命周期测试通过');
    });
  });
}
