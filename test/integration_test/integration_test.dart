// ignore_for_file: avoid_print

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:daily_satori/main.dart' as app;
import 'package:daily_satori/app/routes/app_routes.dart';

/// Daily Satori 集成测试
///
/// 测试覆盖所有核心功能模块
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Daily Satori 全功能集成测试', (WidgetTester tester) async {
    print('\n========================================');
    print('🚀 开始 Daily Satori 集成测试');
    print('========================================\n');

    // ========== 应用启动 ==========
    print('📱 [1/6] 测试应用启动...');

    // 启动应用
    app.main();
    await tester.pumpAndSettle(const Duration(seconds: 30));

    // 验证应用已启动
    expect(find.byType(MaterialApp), findsOneWidget);
    expect(find.byType(Scaffold), findsWidgets);
    print('✅ 应用启动成功');

    // 验证底部导航栏
    final bottomNav = find.byType(BottomNavigationBar);
    expect(bottomNav, findsOneWidget);
    print('✅ 底部导航栏存在');

    // 验证有 5 个导航项
    final navItems = find.descendant(
      of: bottomNav,
      matching: find.byType(BottomNavigationBarItem),
    );
    expect(navItems, findsNWidgets(5));
    print('✅ 导航项数量正确');

    // ========== 导航切换测试 ==========
    print('\n📱 [2/6] 测试导航切换...');

    // 测试切换到各页面（使用实际中文文本）
    await tester.tap(find.text('文章'));
    await tester.pumpAndSettle(const Duration(seconds: 5));
    expect(find.byType(Scaffold), findsWidgets);
    print('✅ 文章页面正常');

    await tester.tap(find.text('日记'));
    await tester.pumpAndSettle(const Duration(seconds: 5));
    expect(find.byType(Scaffold), findsWidgets);
    expect(find.byTooltip('日历'), findsOneWidget);
    expect(find.byTooltip('搜索'), findsOneWidget);
    expect(find.byTooltip('标签'), findsOneWidget);
    print('✅ 日记页面正常');

    await tester.tap(find.text('读书'));
    await tester.pumpAndSettle(const Duration(seconds: 5));
    expect(find.byType(Scaffold), findsWidgets);
    expect(find.byIcon(Icons.search), findsOneWidget);
    print('✅ 读书页面正常');

    await tester.tap(find.text('AI助手'));
    await tester.pumpAndSettle(const Duration(seconds: 5));
    expect(find.byType(Scaffold), findsWidgets);
    expect(find.byType(TextField), findsWidgets);
    print('✅ AI助手页面正常');

    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle(const Duration(seconds: 5));
    expect(find.byType(Scaffold), findsWidgets);
    print('✅ 设置页面正常');

    // 返回首页
    await tester.tap(find.text('文章'));
    await tester.pumpAndSettle(const Duration(seconds: 5));
    expect(find.byType(Scaffold), findsWidgets);
    expect(find.byIcon(Icons.search), findsOneWidget);
    print('✅ 返回文章页面正常');

    // ========== 文章模块测试 ==========
    print('\n📰 [3/6] 测试文章模块...');

    expect(find.byType(Scaffold), findsWidgets);
    expect(find.byIcon(Icons.search), findsOneWidget);
    print('✅ 文章列表加载成功');

    // 点击搜索按钮
    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle(const Duration(seconds: 2));
    print('✅ 搜索按钮可点击');

    // 验证路由配置
    expect(Routes.articleDetail, '/article-detail');
    print('✅ 文章路由配置正确');

    // ========== 路由配置测试 ==========
    print('\n🔗 [4/6] 测试路由配置...');

    expect(Routes.home, '/home');
    expect(Routes.articles, '/articles');
    expect(Routes.diary, '/diary');
    expect(Routes.books, '/books');
    expect(Routes.settings, '/settings');
    expect(Routes.aiChat, '/ai-chat');
    expect(Routes.weeklySummary, '/weekly-summary');
    print('✅ 所有路由配置正确');

    // ========== UI组件和稳定性测试 ==========
    print('\n🎨 [5/6] 测试UI组件和稳定性...');

    expect(find.byType(MaterialApp), findsOneWidget);
    expect(find.byType(Scaffold), findsWidgets);
    expect(find.byType(AppBar), findsWidgets);
    expect(find.byType(BottomNavigationBar), findsOneWidget);
    print('✅ 基本 UI 组件存在');

    // 页面切换稳定性测试
    for (int i = 0; i < 3; i++) {
      await tester.tap(find.text('文章'));
      await tester.pump(const Duration(milliseconds: 500));

      await tester.tap(find.text('日记'));
      await tester.pump(const Duration(milliseconds: 500));

      await tester.tap(find.text('读书'));
      await tester.pump(const Duration(milliseconds: 500));
    }

    expect(find.byType(Scaffold), findsWidgets);
    print('✅ 应用稳定性测试通过');

    print('\n========================================');
    print('✅ 所有集成测试通过！');
    print('========================================\n');
  }, timeout: const Timeout(Duration(minutes: 10)));
}
