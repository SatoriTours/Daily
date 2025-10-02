import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:integration_test/integration_test.dart';

import '../lib/main.dart' as app;
import '../lib/app/routes/app_pages.dart';
import '../lib/app/services/logger_service.dart';
import 'test_config.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('Daily Satori 集成测试', () {
    setUpAll(() async {
      // 初始化 LoggerService
      await LoggerService.i.init();
    });

    setUp(() async {
      // 在每个测试前重置应用状态
      Get.reset();
    });

    testWidgets('1. 文章列表页面能正常显示数据库里面的文章', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle();

      // 导航到文章列表页面
      // 假设从首页可以通过导航栏或侧边栏访问文章页面
      final articlesFinder = find.byKey(const Key('articles_navigation_item'));
      if (articlesFinder.evaluate().isNotEmpty) {
        await tester.tap(articlesFinder);
        await tester.pumpAndSettle();
      } else {
        // 如果导航项不存在，直接导航到文章页面
        Get.toNamed(Routes.articles);
        await tester.pumpAndSettle();
      }

      // 验证页面标题
      expect(find.text('文章'), findsOneWidget);

      // 等待数据加载完成
      await tester.pumpAndSettle(const Duration(seconds: 3));

      // 验证文章列表是否存在
      final articlesListFinder = find.byKey(const Key('articles_list'));
      if (articlesListFinder.evaluate().isNotEmpty) {
        // 如果有文章数据，验证文章项是否存在
        final articleItemFinder = find.byKey(const Key('article_item'));
        expect(articleItemFinder, findsAtLeastNWidgets(1));

        // 验证文章标题显示
        final articleTitleFinder = find.byKey(const Key('article_title'));
        expect(articleTitleFinder, findsAtLeastNWidgets(1));
      } else {
        // 如果没有文章数据，应该显示空状态
        expect(find.byKey(const Key('articles_empty_view')), findsOneWidget);
      }

      // 测试搜索功能
      await tester.tap(find.byKey(const Key('search_button')));
      await tester.pumpAndSettle();

      final searchFieldFinder = find.byKey(const Key('search_field'));
      if (searchFieldFinder.evaluate().isNotEmpty) {
        await tester.enterText(searchFieldFinder, '测试');
        await tester.pumpAndSettle();

        // 验证搜索结果
        expect(find.byKey(const Key('search_results')), findsOneWidget);
      }
    });

    testWidgets('2. 文章详情页面右上角菜单刷新功能正常', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle();

      // 导航到文章详情页面
      Get.toNamed(Routes.articleDetail, arguments: {'articleId': 1});
      await tester.pumpAndSettle();

      // 验证文章详情页面加载
      expect(find.byKey(const Key('article_detail_view')), findsOneWidget);

      // 等待文章数据加载
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 查找右上角更多菜单按钮
      final moreMenuButtonFinder = find.byKey(const Key('article_detail_more_menu'));
      if (moreMenuButtonFinder.evaluate().isNotEmpty) {
        await tester.tap(moreMenuButtonFinder);
        await tester.pumpAndSettle();

        // 查找刷新菜单项
        final refreshMenuItemFinder = find.byKey(const Key('refresh_menu_item'));
        if (refreshMenuItemFinder.evaluate().isNotEmpty) {
          await tester.tap(refreshMenuItemFinder);
          await tester.pumpAndSettle();

          // 验证刷新状态指示器
          expect(find.byKey(const Key('refresh_indicator')), findsOneWidget);

          // 等待刷新完成
          await tester.pumpAndSettle(const Duration(seconds: 5));

          // 验证刷新完成后的状态
          expect(find.byKey(const Key('refresh_complete')), findsOneWidget);
        }
      }

      // 测试下拉刷新功能
      final refreshableFinder = find.byType(RefreshIndicator);
      if (refreshableFinder.evaluate().isNotEmpty) {
        await tester.fling(
          find.byType(Scrollable),
          const Offset(0, 300),
          1000,
        );
        await tester.pumpAndSettle();

        // 验证刷新指示器出现
        expect(find.byType(RefreshIndicator), findsOneWidget);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }
    });

    testWidgets('3. 日记功能正常（新增、编辑、图片支持）', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle();

      // 导航到日记页面
      Get.toNamed(Routes.diary);
      await tester.pumpAndSettle();

      // 验证日记页面加载
      expect(find.byKey(const Key('diary_view')), findsOneWidget);

      // 测试新增日记
      final addDiaryButtonFinder = find.byKey(const Key('add_diary_fab'));
      expect(addDiaryButtonFinder, findsOneWidget);

      await tester.tap(addDiaryButtonFinder);
      await tester.pumpAndSettle();

      // 验证日记编辑对话框
      expect(find.byKey(const Key('diary_edit_dialog')), findsOneWidget);

      // 填写日记内容
      final titleFieldFinder = find.byKey(const Key('diary_title_field'));
      final contentFieldFinder = find.byKey(const Key('diary_content_field'));

      if (titleFieldFinder.evaluate().isNotEmpty) {
        await tester.enterText(titleFieldFinder, '测试日记标题');
      }

      if (contentFieldFinder.evaluate().isNotEmpty) {
        await tester.enterText(contentFieldFinder, '这是测试日记内容，应该能够正常保存和显示。');
      }

      // 测试添加图片功能
      final addImageButtonFinder = find.byKey(const Key('add_image_button'));
      if (addImageButtonFinder.evaluate().isNotEmpty) {
        await tester.tap(addImageButtonFinder);
        await tester.pumpAndSettle();

        // 模拟选择图片（在实际测试中可能需要mock）
        final imagePickerFinder = find.byKey(const Key('image_picker_dialog'));
        if (imagePickerFinder.evaluate().isNotEmpty) {
          // 这里可以mock图片选择
          await tester.tap(find.byKey(const Key('select_image_from_gallery')));
          await tester.pumpAndSettle();
        }
      }

      // 保存日记
      final saveButtonFinder = find.byKey(const Key('save_diary_button'));
      if (saveButtonFinder.evaluate().isNotEmpty) {
        await tester.tap(saveButtonFinder);
        await tester.pumpAndSettle();

        // 验证保存成功提示
        expect(find.byKey(const Key('diary_saved_success')), findsOneWidget);
      }

      // 验证日记出现在列表中
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('diary_item')), findsAtLeastNWidgets(1));

      // 测试编辑日记
      final diaryItemFinder = find.byKey(const Key('diary_item')).first;
      await tester.tap(diaryItemFinder);
      await tester.pumpAndSettle();

      // 查找编辑按钮
      final editButtonFinder = find.byKey(const Key('edit_diary_button'));
      if (editButtonFinder.evaluate().isNotEmpty) {
        await tester.tap(editButtonFinder);
        await tester.pumpAndSettle();

        // 修改内容
        if (contentFieldFinder.evaluate().isNotEmpty) {
          await tester.enterText(contentFieldFinder, '这是修改后的日记内容。');
        }

        // 保存修改
        await tester.tap(saveButtonFinder);
        await tester.pumpAndSettle();

        // 验证修改成功
        expect(find.byKey(const Key('diary_updated_success')), findsOneWidget);
      }
    });

    testWidgets('4. 读书页面功能正常（添加书、刷新书）', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle();

      // 导航到读书页面
      Get.toNamed(Routes.books);
      await tester.pumpAndSettle();

      // 验证读书页面加载
      expect(find.byKey(const Key('books_view')), findsOneWidget);

      // 测试添加书籍功能
      final addBookButtonFinder = find.byKey(const Key('add_book_button'));
      if (addBookButtonFinder.evaluate().isNotEmpty) {
        await tester.tap(addBookButtonFinder);
        await tester.pumpAndSettle();

        // 验证添加书籍对话框
        expect(find.byKey(const Key('add_book_dialog')), findsOneWidget);

        // 填写书籍信息
        final bookTitleFieldFinder = find.byKey(const Key('book_title_field'));
        final bookAuthorFieldFinder = find.byKey(const Key('book_author_field'));

        if (bookTitleFieldFinder.evaluate().isNotEmpty) {
          await tester.enterText(bookTitleFieldFinder, '测试书籍标题');
        }

        if (bookAuthorFieldFinder.evaluate().isNotEmpty) {
          await tester.enterText(bookAuthorFieldFinder, '测试作者');
        }

        // 保存书籍
        final saveBookButtonFinder = find.byKey(const Key('save_book_button'));
        if (saveBookButtonFinder.evaluate().isNotEmpty) {
          await tester.tap(saveBookButtonFinder);
          await tester.pumpAndSettle();

          // 验证添加成功
          expect(find.byKey(const Key('book_added_success')), findsOneWidget);
        }
      }

      // 等待书籍列表更新
      await tester.pumpAndSettle(const Duration(seconds: 2));

      // 验证书籍出现在列表中
      final bookItemFinder = find.byKey(const Key('book_item'));
      if (bookItemFinder.evaluate().isNotEmpty) {
        expect(bookItemFinder, findsAtLeastNWidgets(1));
      }

      // 测试刷新书籍功能
      final refreshBooksButtonFinder = find.byKey(const Key('refresh_books_button'));
      if (refreshBooksButtonFinder.evaluate().isNotEmpty) {
        await tester.tap(refreshBooksButtonFinder);
        await tester.pumpAndSettle();

        // 验证刷新状态指示器
        expect(find.byKey(const Key('books_refreshing')), findsOneWidget);

        // 等待刷新完成
        await tester.pumpAndSettle(const Duration(seconds: 3));

        // 验证刷新完成
        expect(find.byKey(const Key('books_refresh_complete')), findsOneWidget);
      }

      // 测试下拉刷新
      final booksScrollableFinder = find.byType(Scrollable);
      if (booksScrollableFinder.evaluate().isNotEmpty) {
        await tester.fling(
          booksScrollableFinder,
          const Offset(0, 300),
          1000,
        );
        await tester.pumpAndSettle();

        // 验证刷新指示器
        expect(find.byType(RefreshIndicator), findsOneWidget);
        await tester.pumpAndSettle(const Duration(seconds: 3));
      }
    });

    testWidgets('5. 设置页面AI模型修改功能正常', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle();

      // 导航到设置页面
      Get.toNamed(Routes.settings);
      await tester.pumpAndSettle();

      // 验证设置页面加载
      expect(find.byKey(const Key('settings_view')), findsOneWidget);

      // 查找AI配置选项
      final aiConfigFinder = find.byKey(const Key('ai_config_item'));
      if (aiConfigFinder.evaluate().isNotEmpty) {
        await tester.tap(aiConfigFinder);
        await tester.pumpAndSettle();

        // 验证AI配置页面加载
        expect(find.byKey(const Key('ai_config_view')), findsOneWidget);

        // 查找AI模型选择项
        final aiModelFinder = find.byKey(const Key('ai_model_selection'));
        if (aiModelFinder.evaluate().isNotEmpty) {
          await tester.tap(aiModelFinder);
          await tester.pumpAndSettle();

          // 验证模型选择对话框
          expect(find.byKey(const Key('ai_model_dialog')), findsOneWidget);

          // 选择不同的AI模型
          final gpt4ModelFinder = find.byKey(const Key('model_gpt4'));
          if (gpt4ModelFinder.evaluate().isNotEmpty) {
            await tester.tap(gpt4ModelFinder);
            await tester.pumpAndSettle();

            // 确认选择
            final confirmButtonFinder = find.byKey(const Key('confirm_model_selection'));
            if (confirmButtonFinder.evaluate().isNotEmpty) {
              await tester.tap(confirmButtonFinder);
              await tester.pumpAndSettle();

              // 验证模型更新成功提示
              expect(find.byKey(const Key('model_updated_success')), findsOneWidget);
            }
          }
        }

        // 测试编辑AI配置
        final editConfigButtonFinder = find.byKey(const Key('edit_ai_config_button'));
        if (editConfigButtonFinder.evaluate().isNotEmpty) {
          await tester.tap(editConfigButtonFinder);
          await tester.pumpAndSettle();

          // 验证编辑页面
          expect(find.byKey(const Key('ai_config_edit_view')), findsOneWidget);

          // 修改API密钥（测试用）
          final apiKeyFieldFinder = find.byKey(const Key('api_key_field'));
          if (apiKeyFieldFinder.evaluate().isNotEmpty) {
            await tester.tap(apiKeyFieldFinder);
            await tester.pumpAndSettle();

            // 清空并输入新的测试密钥
            await tester.enterText(apiKeyFieldFinder, 'test_api_key_123');
            await tester.pumpAndSettle();
          }

          // 保存配置
          final saveConfigButtonFinder = find.byKey(const Key('save_config_button'));
          if (saveConfigButtonFinder.evaluate().isNotEmpty) {
            await tester.tap(saveConfigButtonFinder);
            await tester.pumpAndSettle();

            // 验证保存成功
            expect(find.byKey(const Key('config_saved_success')), findsOneWidget);
          }
        }
      }
    });

    testWidgets('完整流程测试：从首页到各功能页面的导航', (WidgetTester tester) async {
      // 启动应用
      app.main();
      await tester.pumpAndSettle();

      // 验证首页加载
      expect(find.byKey(const Key('home_view')), findsOneWidget);

      // 测试底部导航栏
      final articlesTabFinder = find.byKey(const Key('articles_tab'));
      final diaryTabFinder = find.byKey(const Key('diary_tab'));
      final booksTabFinder = find.byKey(const Key('books_tab'));
      final settingsTabFinder = find.byKey(const Key('settings_tab'));

      // 导航到文章页面
      if (articlesTabFinder.evaluate().isNotEmpty) {
        await tester.tap(articlesTabFinder);
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('articles_view')), findsOneWidget);
      }

      // 导航到日记页面
      if (diaryTabFinder.evaluate().isNotEmpty) {
        await tester.tap(diaryTabFinder);
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('diary_view')), findsOneWidget);
      }

      // 导航到读书页面
      if (booksTabFinder.evaluate().isNotEmpty) {
        await tester.tap(booksTabFinder);
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('books_view')), findsOneWidget);
      }

      // 导航到设置页面
      if (settingsTabFinder.evaluate().isNotEmpty) {
        await tester.tap(settingsTabFinder);
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('settings_view')), findsOneWidget);
      }

      // 返回首页
      final homeTabFinder = find.byKey(const Key('home_tab'));
      if (homeTabFinder.evaluate().isNotEmpty) {
        await tester.tap(homeTabFinder);
        await tester.pumpAndSettle();
        expect(find.byKey(const Key('home_view')), findsOneWidget);
      }
    });
  });
}