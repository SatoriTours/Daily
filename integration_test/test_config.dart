/// 测试配置文件
/// 用于集成测试的环境配置和通用工具

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';

/// 测试用的常量键
class TestKeys {
  // 页面键
  static const Key homeView = Key('home_view');
  static const Key articlesView = Key('articles_view');
  static const Key articleDetailView = Key('article_detail_view');
  static const Key diaryView = Key('diary_view');
  static const Key booksView = Key('books_view');
  static const Key settingsView = Key('settings_view');

  // 导航键
  static const Key articlesTab = Key('articles_tab');
  static const Key diaryTab = Key('diary_tab');
  static const Key booksTab = Key('books_tab');
  static const Key settingsTab = Key('settings_tab');
  static const Key homeTab = Key('home_tab');

  // 文章相关键
  static const Key articlesList = Key('articles_list');
  static const Key articleItem = Key('article_item');
  static const Key articleTitle = Key('article_title');
  static const Key articlesEmptyView = Key('articles_empty_view');
  static const Key searchButton = Key('search_button');
  static const Key searchField = Key('search_field');
  static const Key searchResults = Key('search_results');

  // 文章详情相关键
  static const Key articleDetailMoreMenu = Key('article_detail_more_menu');
  static const Key refreshMenuItem = Key('refresh_menu_item');
  static const Key refreshIndicator = Key('refresh_indicator');
  static const Key refreshComplete = Key('refresh_complete');

  // 日记相关键
  static const Key addDiaryFab = Key('add_diary_fab');
  static const Key diaryEditDialog = Key('diary_edit_dialog');
  static const Key diaryTitleField = Key('diary_title_field');
  static const Key diaryContentField = Key('diary_content_field');
  static const Key addImageButton = Key('add_image_button');
  static const Key imagePickerDialog = Key('image_picker_dialog');
  static const Key selectImageFromGallery = Key('select_image_from_gallery');
  static const Key saveDiaryButton = Key('save_diary_button');
  static const Key diarySavedSuccess = Key('diary_saved_success');
  static const Key diaryItem = Key('diary_item');
  static const Key editDiaryButton = Key('edit_diary_button');
  static const Key diaryUpdatedSuccess = Key('diary_updated_success');

  // 读书相关键
  static const Key addBookButton = Key('add_book_button');
  static const Key addBookDialog = Key('add_book_dialog');
  static const Key bookTitleField = Key('book_title_field');
  static const Key bookAuthorField = Key('book_author_field');
  static const Key saveBookButton = Key('save_book_button');
  static const Key bookAddedSuccess = Key('book_added_success');
  static const Key bookItem = Key('book_item');
  static const Key refreshBooksButton = Key('refresh_books_button');
  static const Key booksRefreshing = Key('books_refreshing');
  static const Key booksRefreshComplete = Key('books_refresh_complete');

  // 设置相关键
  static const Key aiConfigItem = Key('ai_config_item');
  static const Key aiConfigView = Key('ai_config_view');
  static const Key aiModelSelection = Key('ai_model_selection');
  static const Key aiModelDialog = Key('ai_model_dialog');
  static const Key modelGpt4 = Key('model_gpt4');
  static const Key confirmModelSelection = Key('confirm_model_selection');
  static const Key modelUpdatedSuccess = Key('model_updated_success');
  static const Key editAiConfigButton = Key('edit_ai_config_button');
  static const Key aiConfigEditView = Key('ai_config_edit_view');
  static const Key apiKeyField = Key('api_key_field');
  static const Key saveConfigButton = Key('save_config_button');
  static const Key configSavedSuccess = Key('config_saved_success');
}

/// 测试工具类
class TestUtils {
  /// 等待页面加载完成
  static Future<void> waitForPageLoad(WidgetTester tester, {Duration? duration}) async {
    await tester.pumpAndSettle(duration ?? const Duration(seconds: 3));
  }

  /// 查找并点击按钮
  static Future<void> findAndTap(WidgetTester tester, Key key) async {
    final finder = find.byKey(key);
    expect(finder, findsOneWidget, reason: '找不到键为 ${key.toString()} 的控件');
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  /// 查找并输入文本
  static Future<void> findAndEnterText(WidgetTester tester, Key key, String text) async {
    final finder = find.byKey(key);
    if (finder.evaluate().isNotEmpty) {
      await tester.tap(finder);
      await tester.pumpAndSettle();
      await tester.enterText(finder, text);
      await tester.pumpAndSettle();
    }
  }

  /// 验证控件存在
  static void expectWidgetExists(Key key) {
    expect(find.byKey(key), findsOneWidget, reason: '控件 ${key.toString()} 应该存在');
  }

  /// 验证控件不存在
  static void expectWidgetNotExists(Key key) {
    expect(find.byKey(key), findsNothing, reason: '控件 ${key.toString()} 不应该存在');
  }

  /// 验证提示信息显示
  static void expectSuccessMessage(Key messageKey) {
    expect(find.byKey(messageKey), findsOneWidget, reason: '成功提示信息应该显示');
  }

  /// 模拟下拉刷新
  static Future<void> performPullToRefresh(WidgetTester tester) async {
    final scrollableFinder = find.byType(Scrollable);
    if (scrollableFinder.evaluate().isNotEmpty) {
      await tester.fling(
        scrollableFinder,
        const Offset(0, 300),
        1000,
      );
      await tester.pumpAndSettle();
    }
  }

  /// 重置应用状态
  static void resetAppState() {
    Get.reset();
  }
}

/// 测试数据类
class TestData {
  static const String testDiaryTitle = '测试日记标题';
  static const String testDiaryContent = '这是测试日记内容，应该能够正常保存和显示。';
  static const String testModifiedDiaryContent = '这是修改后的日记内容。';

  static const String testBookTitle = '测试书籍标题';
  static const String testBookAuthor = '测试作者';

  static const String testApiKey = 'test_api_key_123';
  static const String testSearchQuery = '测试';
}

/// 模拟数据类
class MockData {
  static Map<String, dynamic> getMockArticle() {
    return {
      'id': 1,
      'title': '测试文章标题',
      'content': '这是测试文章内容...',
      'summary': '这是文章摘要',
      'tags': ['测试', '技术'],
      'createdAt': DateTime.now().toIso8601String(),
      'updatedAt': DateTime.now().toIso8601String(),
    };
  }

  static Map<String, dynamic> getMockDiary() {
    return {
      'id': 1,
      'title': TestData.testDiaryTitle,
      'content': TestData.testDiaryContent,
      'tags': ['个人', '测试'],
      'createdAt': DateTime.now().toIso8601String(),
      'updatedAt': DateTime.now().toIso8601String(),
      'images': [],
    };
  }

  static Map<String, dynamic> getMockBook() {
    return {
      'id': 1,
      'title': TestData.testBookTitle,
      'author': TestData.testBookAuthor,
      'isbn': '9787123456789',
      'publicationDate': '2023-01-01',
      'summary': '这是测试书籍的简介',
      'notes': [],
      'createdAt': DateTime.now().toIso8601String(),
    };
  }
}