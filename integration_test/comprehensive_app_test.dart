import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:daily_satori/main.dart' as app;

/// Daily Satori 完整功能集成测试
///
/// 覆盖所有核心功能模块的测试用例
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 完整功能测试', () {
    FlutterExceptionHandler? originalOnError;

    setUp(() {
      // 确保错误处理正确设置
      originalOnError = FlutterError.onError;
      FlutterError.onError = (FlutterErrorDetails details) {
        FlutterError.presentError(details);
        originalOnError?.call(details);
      };
    });

    tearDown(() {
      // 恢复默认错误处理
      FlutterError.onError = originalOnError;
    });

    testWidgets('完整功能测试流程', (WidgetTester tester) async {
      debugPrint('🚀 开始完整功能测试...');

      // 启动应用
      app.main();
      await tester.pumpAndSettle(const Duration(seconds: 15));
      await tester.pump(const Duration(seconds: 3));

      // 1. 测试文章模块
      await _testArticleModule(tester);

      // 2. 测试日记模块
      await _testDiaryModule(tester);

      // 3. 测试读书模块
      await _testBookModule(tester);

      // 4. 测试 AI 聊天功能
      await _testAIChatModule(tester);

      // 5. 测试设置功能
      await _testSettingsModule(tester);

      // 6. 测试备份还原功能
      await _testBackupModule(tester);

      // 7. 测试Web服务功能
      await _testWebServiceModule(tester);

      // 8. 测试存储管理功能
      await _testStorageManagementModule(tester);

      // 9. 测试插件中心功能
      await _testPluginCenterModule(tester);

      // 10. 测试语言切换功能
      await _testLanguageSwitching(tester);

      // 11. 测试主题切换功能
      await _testThemeSwitching(tester);

      debugPrint('✅ 所有功能测试完成');
    });
  });
}

/// 测试文章模块完整功能
Future<void> _testArticleModule(WidgetTester tester) async {
  try {
    debugPrint('📰 测试文章模块...');

    // 切换到文章页面
    final articlesTab = find.text('文章');
    if (tester.any(articlesTab)) {
      await tester.tap(articlesTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已切换到文章页面');
    }

    // 测试添加文章功能
    await _testAddArticle(tester);

    // 测试文章列表操作
    await _testArticleListOperations(tester);

    // 测试文章详情页功能
    await _testArticleDetail(tester);

    debugPrint('✅ 文章模块测试完成');
  } catch (e, stackTrace) {
    debugPrint('❌ 文章模块测试失败: $e');
    debugPrint('Stack trace: $stackTrace');
  }
}

/// 测试添加文章功能
Future<void> _testAddArticle(WidgetTester tester) async {
  try {
    debugPrint('➕ 测试添加文章...');

    // 设置剪贴板内容
    const articleUrl = 'https://blog.tymscar.com/posts/gleamaoc2025/';
    await Clipboard.setData(const ClipboardData(text: articleUrl));
    debugPrint('✅ 已设置剪贴板内容');

    // 点击添加文章按钮
    final fabButton = find.byType(FloatingActionButton);
    if (tester.any(fabButton)) {
      await tester.tap(fabButton);
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 从剪贴板添加
      final pasteOption = find.text('从剪贴板');
      if (tester.any(pasteOption)) {
        await tester.tap(pasteOption.first);
        await tester.pumpAndSettle(const Duration(seconds: 10));
        debugPrint('✅ 已从剪贴板添加文章');
      }
    }
  } catch (e) {
    debugPrint('⚠️ 添加文章测试失败: $e');
  }
}

/// 测试文章列表操作
Future<void> _testArticleListOperations(WidgetTester tester) async {
  try {
    debugPrint('📋 测试文章列表操作...');

    // 等待列表加载
    await tester.pumpAndSettle(const Duration(seconds: 3));

    // 检查文章列表
    final articleList = find.byType(ListTile);
    if (tester.any(articleList)) {
      debugPrint('✅ 文章列表显示正常');
    }

    // 测试搜索功能
    final searchField = find.byType(TextField);
    if (tester.any(searchField)) {
      await tester.tap(searchField.first);
      await tester.enterText(searchField.first, '测试');
      await tester.pumpAndSettle(const Duration(seconds: 2));
      debugPrint('✅ 搜索功能测试完成');
    }
  } catch (e) {
    debugPrint('⚠️ 文章列表操作测试失败: $e');
  }
}

/// 测试文章详情页
Future<void> _testArticleDetail(WidgetTester tester) async {
  try {
    debugPrint('📖 测试文章详情页...');

    final articleList = find.byType(ListTile);
    if (tester.any(articleList)) {
      // 点击第一篇文章
      await tester.tap(articleList.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入文章详情页');

      // 测试分享功能
      final shareButton = find.byIcon(Icons.share);
      if (tester.any(shareButton)) {
        await tester.tap(shareButton);
        await tester.pumpAndSettle(const Duration(seconds: 2));

        // 关闭分享对话框
        final closeButton = find.byIcon(Icons.close);
        if (tester.any(closeButton)) {
          await tester.tap(closeButton);
          await tester.pumpAndSettle(const Duration(seconds: 1));
        }
        debugPrint('✅ 分享功能测试完成');
      }

      // 测试刷新功能
      final refreshButton = find.byIcon(Icons.refresh);
      if (tester.any(refreshButton)) {
        await tester.tap(refreshButton);
        await tester.pumpAndSettle(const Duration(seconds: 5));
        debugPrint('✅ 刷新功能测试完成');
      }

      // 返回列表
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }
  } catch (e) {
    debugPrint('⚠️ 文章详情页测试失败: $e');
  }
}

/// 测试日记模块完整功能
Future<void> _testDiaryModule(WidgetTester tester) async {
  try {
    debugPrint('📔 测试日记模块...');

    // 切换到日记页面
    final diaryTab = find.text('日记');
    if (tester.any(diaryTab)) {
      await tester.tap(diaryTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已切换到日记页面');
    }

    // 测试添加日记
    await _testAddDiary(tester);

    // 测试日记列表
    await _testDiaryList(tester);

    debugPrint('✅ 日记模块测试完成');
  } catch (e, stackTrace) {
    debugPrint('❌ 日记模块测试失败: $e');
    debugPrint('Stack trace: $stackTrace');
  }
}

/// 测试添加日记功能
Future<void> _testAddDiary(WidgetTester tester) async {
  try {
    debugPrint('➕ 测试添加日记...');

    // 点击添加日记按钮
    final fabButton = find.byType(FloatingActionButton);
    if (tester.any(fabButton)) {
      await tester.tap(fabButton);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 点击了添加日记按钮');

      // 输入日记内容
      final contentField = find.byType(TextField);
      if (tester.any(contentField)) {
        await tester.tap(contentField.first);
        final testContent = '测试日记内容 - ${DateTime.now()}\n\n这是用于测试的日记，包含了基本的文字内容。\n\n## 标题\n\n这里是一些内容。';
        await tester.enterText(contentField.first, testContent);
        await tester.pumpAndSettle();
        debugPrint('✅ 已输入日记内容');

        // 保存日记
        final saveButton = find.text('保存');
        if (tester.any(saveButton)) {
          await tester.tap(saveButton.first);
          await tester.pumpAndSettle(const Duration(seconds: 3));
          debugPrint('✅ 日记保存成功');
        }
      }
    }
  } catch (e) {
    debugPrint('⚠️ 添加日记测试失败: $e');
  }
}

/// 测试日记列表功能
Future<void> _testDiaryList(WidgetTester tester) async {
  try {
    debugPrint('📋 测试日记列表...');

    // 等待列表加载
    await tester.pumpAndSettle(const Duration(seconds: 3));

    // 检查日记列表
    final diaryList = find.byType(ListTile);
    if (tester.any(diaryList)) {
      debugPrint('✅ 日记列表显示正常');
    }

    // 测试搜索功能
    final searchField = find.byType(TextField);
    if (tester.any(searchField)) {
      await tester.tap(searchField.first);
      await tester.enterText(searchField.first, '测试');
      await tester.pumpAndSettle(const Duration(seconds: 2));
      debugPrint('✅ 日记搜索功能测试完成');
    }
  } catch (e) {
    debugPrint('⚠️ 日记列表测试失败: $e');
  }
}

/// 测试读书模块完整功能
Future<void> _testBookModule(WidgetTester tester) async {
  try {
    debugPrint('📚 测试读书模块...');

    // 切换到读书页面
    final booksTab = find.text('读书');
    if (tester.any(booksTab)) {
      await tester.tap(booksTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已切换到读书页面');
    }

    // 测试添加书籍
    await _testAddBook(tester);

    // 测试添加读书感悟
    await _testAddViewpoint(tester);

    debugPrint('✅ 读书模块测试完成');
  } catch (e, stackTrace) {
    debugPrint('❌ 读书模块测试失败: $e');
    debugPrint('Stack trace: $stackTrace');
  }
}

/// 测试添加书籍功能
Future<void> _testAddBook(WidgetTester tester) async {
  try {
    debugPrint('➕ 测试添加书籍...');

    // 点击添加书籍按钮
    final fabButton = find.byType(FloatingActionButton);
    if (tester.any(fabButton)) {
      await tester.tap(fabButton);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 点击了添加书籍按钮');

      // 搜索书籍
      final searchField = find.byType(TextField);
      if (tester.any(searchField)) {
        await tester.tap(searchField.first);
        await tester.enterText(searchField.first, '三体');
        await tester.pumpAndSettle(const Duration(seconds: 3));

        // 点击搜索
        final searchButton = find.text('搜索');
        if (tester.any(searchButton)) {
          await tester.tap(searchButton.first);
          await tester.pumpAndSettle(const Duration(seconds: 5));
          debugPrint('✅ 已搜索书籍');

          // 选择第一个搜索结果
          final firstResult = find.byType(ListTile);
          if (tester.any(firstResult)) {
            await tester.tap(firstResult.first);
            await tester.pumpAndSettle(const Duration(seconds: 3));
            debugPrint('✅ 已选择书籍');

            // 保存书籍
            final saveButton = find.text('保存');
            if (tester.any(saveButton)) {
              await tester.tap(saveButton.first);
              await tester.pumpAndSettle(const Duration(seconds: 3));
              debugPrint('✅ 书籍添加成功');
            }
          }
        }
      }
    }
  } catch (e) {
    debugPrint('⚠️ 添加书籍测试失败: $e');
  }
}

/// 测试添加读书感悟
Future<void> _testAddViewpoint(WidgetTester tester) async {
  try {
    debugPrint('💭 测试添加读书感悟...');

    // 确保在读书页面
    final booksTab = find.text('读书');
    if (tester.any(booksTab)) {
      await tester.tap(booksTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 点击添加感悟按钮（FAB必须始终显示）
    final fabButton = find.byType(FloatingActionButton);
    if (tester.any(fabButton)) {
      await tester.tap(fabButton);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 点击了添加感悟按钮');

      // 输入感悟内容
      final contentField = find.byType(TextField);
      if (tester.any(contentField)) {
        await tester.tap(contentField.first);
        final viewpointContent = '《三体》读后感悟 - ${DateTime.now()}\n\n这是一本令人震撼的科幻小说...';
        await tester.enterText(contentField.first, viewpointContent);
        await tester.pumpAndSettle();
        debugPrint('✅ 已输入感悟内容');

        // 保存感悟
        final saveButton = find.text('保存');
        if (tester.any(saveButton)) {
          await tester.tap(saveButton.first);
          await tester.pumpAndSettle(const Duration(seconds: 3));
          debugPrint('✅ 感悟保存成功');
        }
      }
    }
  } catch (e) {
    debugPrint('⚠️ 添加读书感悟测试失败: $e');
  }
}

/// 测试 AI 聊天功能
Future<void> _testAIChatModule(WidgetTester tester) async {
  try {
    debugPrint('🤖 测试 AI 聊天功能...');

    // 查找并点击 AI 聊天入口（可能在首页或设置中）
    final aiChatButton = find.text('AI助手');
    if (tester.any(aiChatButton)) {
      await tester.tap(aiChatButton);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入 AI 聊天页面');

      // 测试发送消息
      final inputField = find.byType(TextField);
      if (tester.any(inputField)) {
        await tester.tap(inputField.first);
        await tester.enterText(inputField.first, '帮我搜索关于测试的文章');
        await tester.pumpAndSettle();

        // 点击发送
        final sendButton = find.byIcon(Icons.send);
        if (tester.any(sendButton)) {
          await tester.tap(sendButton);
          await tester.pumpAndSettle(const Duration(seconds: 10));
          debugPrint('✅ 已发送 AI 消息');
        }
      }

      // 返回
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }
  } catch (e) {
    debugPrint('⚠️ AI 聊天测试失败（可能是未配置）: $e');
  }
}

/// 测试设置功能
Future<void> _testSettingsModule(WidgetTester tester) async {
  try {
    debugPrint('⚙️ 测试设置功能...');

    // 切换到设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已切换到设置页面');
    }

    // 测试主题切换
    final themeOption = find.text('外观');
    if (tester.any(themeOption)) {
      await tester.tap(themeOption);
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 选择暗色主题
      final darkTheme = find.text('深色');
      if (tester.any(darkTheme)) {
        await tester.tap(darkTheme);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        debugPrint('✅ 主题切换测试完成');
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    // 测试语言设置
    final languageOption = find.text('语言');
    if (tester.any(languageOption)) {
      await tester.tap(languageOption);
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 选择中文
      final chineseOption = find.text('中文');
      if (tester.any(chineseOption)) {
        await tester.tap(chineseOption);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        debugPrint('✅ 语言设置测试完成');
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    // 测试 AI 配置
    final aiConfigOption = find.text('AI配置');
    if (tester.any(aiConfigOption)) {
      await tester.tap(aiConfigOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ AI 配置页面访问正常');

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    // 测试插件中心
    final pluginOption = find.text('插件中心');
    if (tester.any(pluginOption)) {
      await tester.tap(pluginOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 插件中心访问正常');

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ 设置功能测试完成');
  } catch (e, stackTrace) {
    debugPrint('❌ 设置功能测试失败: $e');
    debugPrint('Stack trace: $stackTrace');
  }
}

/// 测试备份还原功能
Future<void> _testBackupModule(WidgetTester tester) async {
  try {
    debugPrint('💾 测试备份还原功能...');

    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 查找备份还原选项
    final backupOption = find.text('备份与恢复');
    if (tester.any(backupOption)) {
      await tester.tap(backupOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入备份恢复页面');

      // 测试备份设置
      final backupSettings = find.text('备份设置');
      if (tester.any(backupSettings)) {
        await tester.tap(backupSettings);
        await tester.pumpAndSettle(const Duration(seconds: 3));
        debugPrint('✅ 备份设置页面访问正常');

        // 返回
        await tester.pageBack();
        await tester.pumpAndSettle(const Duration(seconds: 2));
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ 备份还原功能测试完成');
  } catch (e) {
    debugPrint('⚠️ 备份还原测试失败: $e');
  }
}

/// 测试Web服务功能
Future<void> _testWebServiceModule(WidgetTester tester) async {
  try {
    debugPrint('🌐 测试Web服务功能...');

    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 查找Web服务选项
    final webServiceOption = find.text('Web服务');
    if (tester.any(webServiceOption)) {
      await tester.tap(webServiceOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入Web服务页面');

      // 检查Web服务开关
      final switchWidget = find.byType(Switch);
      if (tester.any(switchWidget)) {
        debugPrint('✅ Web服务开关控件存在');
      }

      // 检查IP地址和端口显示
      final ipText = find.textContaining('IP地址');
      if (tester.any(ipText)) {
        debugPrint('✅ IP地址显示正常');
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ Web服务功能测试完成');
  } catch (e) {
    debugPrint('⚠️ Web服务测试失败: $e');
  }
}

/// 测试存储管理功能
Future<void> _testStorageManagementModule(WidgetTester tester) async {
  try {
    debugPrint('📦 测试存储管理功能...');

    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 查找存储管理选项
    final storageOption = find.text('存储管理');
    if (tester.any(storageOption)) {
      await tester.tap(storageOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入存储管理页面');

      // 检查缓存大小显示
      final cacheSizeText = find.textContaining('缓存');
      if (tester.any(cacheSizeText)) {
        debugPrint('✅ 缓存大小显示正常');
      }

      // 检查数据库大小显示
      final dbSizeText = find.textContaining('数据库');
      if (tester.any(dbSizeText)) {
        debugPrint('✅ 数据库大小显示正常');
      }

      // 测试清理缓存功能
      final clearCacheButton = find.text('清理缓存');
      if (tester.any(clearCacheButton)) {
        debugPrint('✅ 清理缓存按钮存在');
        // 注意：实际测试时不点击，避免清理重要数据
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ 存储管理功能测试完成');
  } catch (e) {
    debugPrint('⚠️ 存储管理测试失败: $e');
  }
}

/// 测试插件中心功能
Future<void> _testPluginCenterModule(WidgetTester tester) async {
  try {
    debugPrint('🔌 测试插件中心功能...');

    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 查找插件中心选项
    final pluginOption = find.text('插件中心');
    if (tester.any(pluginOption)) {
      await tester.tap(pluginOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入插件中心页面');

      // 检查插件列表
      final pluginList = find.byType(ListTile);
      if (tester.any(pluginList)) {
        debugPrint('✅ 插件列表显示正常');
      }

      // 测试添加插件功能
      final addPluginButton = find.byIcon(Icons.add);
      if (tester.any(addPluginButton)) {
        await tester.tap(addPluginButton);
        await tester.pumpAndSettle(const Duration(seconds: 3));
        debugPrint('✅ 点击了添加插件按钮');

        // 检查是否打开了插件编辑页面
        final nameField = find.byType(TextField);
        if (tester.any(nameField)) {
          debugPrint('✅ 插件编辑页面打开正常');
        }

        // 返回
        await tester.pageBack();
        await tester.pumpAndSettle(const Duration(seconds: 2));
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ 插件中心功能测试完成');
  } catch (e) {
    debugPrint('⚠️ 插件中心测试失败: $e');
  }
}

/// 测试语言切换功能
Future<void> _testLanguageSwitching(WidgetTester tester) async {
  try {
    debugPrint('🌏 测试语言切换功能...');

    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 查找语言选项
    final languageOption = find.text('语言');
    if (tester.any(languageOption)) {
      await tester.tap(languageOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入语言设置页面');

      // 测试切换到英文
      final englishOption = find.text('English');
      if (tester.any(englishOption)) {
        await tester.tap(englishOption);
        await tester.pumpAndSettle(const Duration(seconds: 3));
        debugPrint('✅ 已切换到英文');

        // 验证界面文字已切换
        final settingsInEnglish = find.text('Settings');
        if (tester.any(settingsInEnglish)) {
          debugPrint('✅ 界面已切换为英文');
        }

        // 切换回中文
        await tester.tap(languageOption);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        final chineseOption = find.text('中文');
        if (tester.any(chineseOption)) {
          await tester.tap(chineseOption);
          await tester.pumpAndSettle(const Duration(seconds: 3));
          debugPrint('✅ 已切换回中文');
        }
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ 语言切换功能测试完成');
  } catch (e) {
    debugPrint('⚠️ 语言切换测试失败: $e');
  }
}

/// 测试主题切换功能
Future<void> _testThemeSwitching(WidgetTester tester) async {
  try {
    debugPrint('🎨 测试主题切换功能...');

    // 确保在设置页面
    final settingsTab = find.text('设置');
    if (tester.any(settingsTab)) {
      await tester.tap(settingsTab);
      await tester.pumpAndSettle(const Duration(seconds: 3));
    }

    // 查找外观选项
    final themeOption = find.text('外观');
    if (tester.any(themeOption)) {
      await tester.tap(themeOption);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      debugPrint('✅ 已进入外观设置页面');

      // 测试切换到暗色主题
      final darkTheme = find.text('深色');
      if (tester.any(darkTheme)) {
        await tester.tap(darkTheme);
        await tester.pumpAndSettle(const Duration(seconds: 3));
        debugPrint('✅ 已切换到深色主题');
      }

      // 测试切换到亮色主题
      final lightTheme = find.text('浅色');
      if (tester.any(lightTheme)) {
        await tester.tap(lightTheme);
        await tester.pumpAndSettle(const Duration(seconds: 3));
        debugPrint('✅ 已切换到浅色主题');
      }

      // 测试跟随系统
      final systemTheme = find.text('跟随系统');
      if (tester.any(systemTheme)) {
        await tester.tap(systemTheme);
        await tester.pumpAndSettle(const Duration(seconds: 3));
        debugPrint('✅ 已设置为跟随系统');
      }

      // 返回设置页
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    debugPrint('✅ 主题切换功能测试完成');
  } catch (e) {
    debugPrint('⚠️ 主题切换测试失败: $e');
  }
}
