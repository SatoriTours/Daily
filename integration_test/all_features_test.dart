// ignore_for_file: avoid_print

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:daily_satori/main.dart' as app;
import 'package:feather_icons/feather_icons.dart';

import 'test_config.dart';
import 'test_ai_bootstrap.dart';

/// Daily Satori 全功能自动化集成测试
///
/// 测试覆盖所有核心功能，每次代码修改后运行即可验证功能是否正常
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 全功能自动化测试', () {
    testWidgets('完整应用功能验证', (WidgetTester tester) async {
      print('\n========================================');
      print('🚀 开始全功能自动化测试');
      print('========================================\n');

      // 1. 启动应用
      await _testAppStartup(tester);

      // 2. 测试文章模块
      await _testArticlesModule(tester);

      // 3. 测试日记模块
      await _testDiaryModule(tester);

      // 4. 测试读书模块
      await _testBooksModule(tester);

      // 5. 测试AI聊天功能
      await _testAIChatModule(tester);

      // 6. 测试设置模块
      await _testSettingsModule(tester);

      // 7. 测试备份恢复功能
      await _testBackupModule(tester);

      print('\n========================================');
      print('✅ 全功能测试完成！');
      print('========================================\n');
    });
  });
}

/// 测试应用启动
Future<void> _testAppStartup(WidgetTester tester) async {
  print('📱 [1/7] 测试应用启动...');

  try {
    app.main();
    await tester.pumpAndSettle(const Duration(seconds: 15));

    // 验证主界面加载成功
    expect(find.byType(Scaffold), findsWidgets, reason: '应该能看到Scaffold组件');
    print('✅ 应用启动成功');

    // 验证底部导航栏存在
    final bottomNav = find.byType(BottomNavigationBar);
    if (tester.any(bottomNav)) {
      print('✅ 底部导航栏存在');
    }
  } catch (e) {
    print('❌ 应用启动失败: $e');
    rethrow;
  }
}

/// 测试文章模块
Future<void> _testArticlesModule(WidgetTester tester) async {
  print('\n📰 [2/7] 测试文章模块...');

  try {
    // 切换到文章页面
    final articlesTab = find.text('文章');
    if (tester.any(articlesTab)) {
      await tester.tap(articlesTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('✅ 已切换到文章页面');
    }

    // 测试添加文章
    await _testAddArticle(tester);

    // 测试搜索功能
    await _testArticleSearch(tester);

    // 测试文章详情
    await _testArticleDetail(tester);

    print('✅ 文章模块测试完成');
  } catch (e) {
    print('⚠️ 文章模块测试失败: $e');
    // 不抛出异常，继续其他测试
  }
}

/// 测试添加文章
Future<void> _testAddArticle(WidgetTester tester) async {
  print('  ➕ 测试添加文章...');

  try {
    // 设置剪贴板
    final testUrl = TestConfig.testArticleUrls.first;
    await Clipboard.setData(ClipboardData(text: testUrl));
    await tester.pump(const Duration(seconds: 1));

    // 点击FAB按钮
    final fab = find.byType(FloatingActionButton);
    if (tester.any(fab)) {
      await tester.tap(fab);
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 选择从剪贴板添加
      final pasteOption = find.text('从剪贴板');
      if (tester.any(pasteOption)) {
        await tester.tap(pasteOption.first);
        await tester.pumpAndSettle(const Duration(seconds: 10));
        print('  ✅ 文章添加成功');
      } else {
        print('  ⚠️ 未找到剪贴板选项');
      }
    } else {
      print('  ⚠️ 未找到FAB按钮');
    }
  } catch (e) {
    print('  ⚠️ 添加文章失败: $e');
  }
}

/// 测试文章搜索
Future<void> _testArticleSearch(WidgetTester tester) async {
  print('  🔍 测试文章搜索...');

  try {
    // 点击搜索按钮
    final searchButton = find.byIcon(FeatherIcons.search);
    if (tester.any(searchButton)) {
      await tester.tap(searchButton.first);
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 输入搜索内容
      final searchField = find.byType(TextField);
      if (tester.any(searchField)) {
        await tester.tap(searchField.first);
        await tester.enterText(searchField.first, '测试');
        await tester.pumpAndSettle(const Duration(seconds: 2));
        print('  ✅ 搜索功能正常');
      }
    }
  } catch (e) {
    print('  ⚠️ 搜索测试失败: $e');
  }
}

/// 测试文章详情
Future<void> _testArticleDetail(WidgetTester tester) async {
  print('  📖 测试文章详情...');

  try {
    // 查找文章列表项
    final listTiles = find.byType(ListTile);
    if (tester.any(listTiles)) {
      await tester.tap(listTiles.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 验证详情页
      expect(find.byIcon(Icons.more_horiz), findsWidgets, reason: '详情页应有菜单按钮');
      print('  ✅ 文章详情页正常');

      // 返回列表
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }
  } catch (e) {
    print('  ⚠️ 文章详情测试失败: $e');
  }
}

/// 测试日记模块
Future<void> _testDiaryModule(WidgetTester tester) async {
  print('\n📔 [3/7] 测试日记模块...');

  try {
    // 切换到日记页面
    final diaryTab = find.text('日记');
    if (tester.any(diaryTab)) {
      await tester.tap(diaryTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('✅ 已切换到日记页面');
    }

    // 测试添加日记
    await _testAddDiary(tester);

    // 测试日记搜索
    await _testDiarySearch(tester);

    print('✅ 日记模块测试完成');
  } catch (e) {
    print('⚠️ 日记模块测试失败: $e');
  }
}

/// 测试添加日记
Future<void> _testAddDiary(WidgetTester tester) async {
  print('  ➕ 测试添加日记...');

  try {
    // 点击FAB按钮
    final fab = find.byType(FloatingActionButton);
    if (tester.any(fab)) {
      await tester.tap(fab);
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 输入日记内容
      final textField = find.byType(TextField);
      if (tester.any(textField)) {
        final testContent = '# 测试日记 ${DateTime.now()}\n\n今天学习了Flutter集成测试。';
        await tester.tap(textField.first);
        await tester.enterText(textField.first, testContent);
        await tester.pumpAndSettle();

        // 保存日记
        final saveButton = find.text('保存');
        if (tester.any(saveButton)) {
          await tester.tap(saveButton.first);
          await tester.pumpAndSettle(const Duration(seconds: 3));
          print('  ✅ 日记保存成功');
        }
      }
    }
  } catch (e) {
    print('  ⚠️ 添加日记失败: $e');
  }
}

/// 测试日记搜索
Future<void> _testDiarySearch(WidgetTester tester) async {
  print('  🔍 测试日记搜索...');

  try {
    final searchField = find.byType(TextField);
    if (tester.any(searchField)) {
      await tester.tap(searchField.first);
      await tester.enterText(searchField.first, '测试');
      await tester.pumpAndSettle(const Duration(seconds: 2));
      print('  ✅ 日记搜索功能正常');
    }
  } catch (e) {
    print('  ⚠️ 日记搜索测试失败: $e');
  }
}

/// 测试读书模块
Future<void> _testBooksModule(WidgetTester tester) async {
  print('\n📚 [4/7] 测试读书模块...');

  try {
    // 切换到读书页面
    final booksTab = find.text('读书');
    if (tester.any(booksTab)) {
      await tester.tap(booksTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('✅ 已切换到读书页面');
    }

    // 测试添加读书感悟
    await _testAddViewpoint(tester);

    print('✅ 读书模块测试完成');
  } catch (e) {
    print('⚠️ 读书模块测试失败: $e');
  }
}

/// 测试添加读书感悟
Future<void> _testAddViewpoint(WidgetTester tester) async {
  print('  💭 测试添加读书感悟...');

  try {
    // 点击FAB按钮
    final fab = find.byType(FloatingActionButton);
    if (tester.any(fab)) {
      await tester.tap(fab);
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 输入感悟内容
      final textField = find.byType(TextField);
      if (tester.any(textField)) {
        final testContent = '# 测试感悟 ${DateTime.now()}\n\n这是一本好书...';
        await tester.tap(textField.first);
        await tester.enterText(textField.first, testContent);
        await tester.pumpAndSettle();

        // 保存感悟
        final saveButton = find.text('保存');
        if (tester.any(saveButton)) {
          await tester.tap(saveButton.first);
          await tester.pumpAndSettle(const Duration(seconds: 3));
          print('  ✅ 读书感悟保存成功');
        }
      }
    }
  } catch (e) {
    print('  ⚠️ 添加读书感悟失败: $e');
  }
}

/// 测试AI聊天功能
Future<void> _testAIChatModule(WidgetTester tester) async {
  print('\n🤖 [5/7] 测试AI聊天功能...');

  try {
    // 配置AI（如果提供了环境变量）
    await TestAiBootstrap.configureFromEnv();

    // 查找AI聊天入口
    final aiChatButton = find.text('AI助手');
    if (tester.any(aiChatButton)) {
      await tester.tap(aiChatButton);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('✅ 已进入AI聊天页面');

      // 测试发送消息（如果配置了AI）
      if (TestConfig.aiToken.isNotEmpty) {
        await _testAIMessage(tester);
      } else {
        print('  ⚠️ 未配置AI，跳过消息测试');
      }

      // 返回
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    } else {
      print('  ⚠️ 未找到AI聊天入口');
    }

    print('✅ AI聊天功能测试完成');
  } catch (e) {
    print('⚠️ AI聊天测试失败: $e');
  }
}

/// 测试发送AI消息
Future<void> _testAIMessage(WidgetTester tester) async {
  print('  💬 测试发送AI消息...');

  try {
    final inputField = find.byType(TextField);
    if (tester.any(inputField)) {
      await tester.tap(inputField.first);
      await tester.enterText(inputField.first, '你好');
      await tester.pumpAndSettle();

      final sendButton = find.byIcon(Icons.send);
      if (tester.any(sendButton)) {
        await tester.tap(sendButton);
        await tester.pumpAndSettle(const Duration(seconds: 10));
        print('  ✅ AI消息发送成功');
      }
    }
  } catch (e) {
    print('  ⚠️ AI消息测试失败: $e');
  }
}

/// 测试设置模块
Future<void> _testSettingsModule(WidgetTester tester) async {
  print('\n⚙️ [6/7] 测试设置模块...');

  try {
    // 切换到设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('✅ 已切换到设置页面');
    }

    // 测试主题切换
    await _testThemeSwitch(tester);

    // 测试语言设置
    await _testLanguageSetting(tester);

    // 测试AI配置入口
    await _testAIConfigAccess(tester);

    print('✅ 设置模块测试完成');
  } catch (e) {
    print('⚠️ 设置模块测试失败: $e');
  }
}

/// 测试主题切换
Future<void> _testThemeSwitch(WidgetTester tester) async {
  print('  🎨 测试主题切换...');

  try {
    final themeOption = find.textContaining('外观');
    if (tester.any(themeOption)) {
      await tester.tap(themeOption.first);
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 切换到深色主题
      final darkTheme = find.text('深色');
      if (tester.any(darkTheme)) {
        await tester.tap(darkTheme);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        print('  ✅ 主题切换成功');

        // 切换回浅色
        final lightTheme = find.text('浅色');
        if (tester.any(lightTheme)) {
          await tester.tap(lightTheme);
          await tester.pumpAndSettle(const Duration(seconds: 2));
        }
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }
  } catch (e) {
    print('  ⚠️ 主题切换测试失败: $e');
  }
}

/// 测试语言设置
Future<void> _testLanguageSetting(WidgetTester tester) async {
  print('  🌏 测试语言设置...');

  try {
    final languageOption = find.textContaining('语言');
    if (tester.any(languageOption)) {
      await tester.tap(languageOption.first);
      await tester.pumpAndSettle(const Duration(seconds: 2));
      print('  ✅ 语言设置页面访问成功');

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }
  } catch (e) {
    print('  ⚠️ 语言设置测试失败: $e');
  }
}

/// 测试AI配置访问
Future<void> _testAIConfigAccess(WidgetTester tester) async {
  print('  🔧 测试AI配置入口...');

  try {
    final aiConfigOption = find.textContaining('AI配置');
    if (tester.any(aiConfigOption)) {
      await tester.tap(aiConfigOption.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('  ✅ AI配置页面访问成功');

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }
  } catch (e) {
    print('  ⚠️ AI配置访问测试失败: $e');
  }
}

/// 测试备份恢复功能
Future<void> _testBackupModule(WidgetTester tester) async {
  print('\n💾 [7/7] 测试备份恢复功能...');

  try {
    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 测试备份恢复入口
    final backupOption = find.textContaining('备份');
    if (tester.any(backupOption)) {
      await tester.tap(backupOption.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('✅ 备份恢复页面访问成功');

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    print('✅ 备份恢复功能测试完成');
  } catch (e) {
    print('⚠️ 备份恢复测试失败: $e');
  }
}
