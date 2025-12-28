// ignore_for_file: avoid_print

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_html/flutter_html.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:integration_test/integration_test.dart';
import 'package:feather_icons/feather_icons.dart';

import 'package:daily_satori/app/components/search/generic_search_bar.dart';
import 'package:daily_satori/app/pages/articles/views/widgets/article_card.dart';

import 'test_config.dart';
import 'test_ai_bootstrap.dart';
import 'test_utils.dart';

/// 文章收藏功能完整集成测试
///
/// 测试流程：
/// 1. 启动应用并自动检测剪切板URL
/// 2. 保存文章并触发AI分析
/// 3. 验证文章在列表中显示
/// 4. 测试收藏/取消收藏功能
/// 5. 测试搜索功能
/// 6. 进入文章详情页
/// 7. 测试刷新、更新、删除功能
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('文章收藏功能测试', () {
    setUpAll(() async {
      // 设置剪切板内容为测试URL
      await Clipboard.setData(ClipboardData(text: TestConfig.testArticleUrls.first));
    });

    testWidgets('完整的文章收藏流程测试', (WidgetTester tester) async {
      try {
        // 1. 启动应用
        logInfo('启动应用...');
        await IntegrationTestUtils.safeStartApp(tester);
        await TestAiBootstrap.configureFromEnv();
        await IntegrationTestUtils.waitForPageStable(tester);
        await _visualPause(tester);

        // 等待剪切板检测对话框自动弹出
        logInfo('等待剪切板检测对话框...');

        await _waitForWidgetOrFail(tester, find.text('发现URL'), const Duration(seconds: 15), reason: '应该弹出剪切板URL确认对话框');
        logSuccess('检测到剪切板URL对话框');
        await _visualPause(tester);

        // 2. 点击“确定”，进入保存链接页（ShareDialog）
        await _tapTextOrFail(tester, '确定');
        await _waitForShareDialog(tester, timeout: const Duration(seconds: 15));
        await _visualPause(tester);

        // 3. 在 share dialog 页面点击“保存”，开始 AI 分析过程
        logInfo('在保存链接页点击保存，开始AI分析...');
        await _tapTextOrFail(tester, '保存');
        await _visualPause(tester);

        // 4. 等待保存/分析结束回到文章列表，并出现文章卡片
        await _waitForArticlesListWithAtLeastOneCard(tester, timeout: const Duration(seconds: 120));
        await _visualPause(tester);

        final articleTitle = _tryExtractTitleFromFirstCard(tester);
        logInfo('捕获文章标题片段: ${articleTitle ?? "<unknown>"}');

        // 4.1 在列表页：收藏/取消收藏
        await _toggleFavoriteInFirstCard(tester);
        await _visualPause(tester);
        await _toggleFavoriteInFirstCard(tester);
        await _visualPause(tester);

        // 5. 搜索文章返回正确结果
        await _searchArticleAndAssertResult(tester, query: articleTitle);
        await _visualPause(tester);

        // 6. 点击文章进入详情页，右上角有刷新/删除等功能
        await _openFirstArticleDetail(tester);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        await _visualPause(tester);

        // 6.1 点击“原文”标签，验证原文区域可正常显示
        await _openOriginalTabAndAssertVisible(tester);
        await _visualPause(tester);

        await _openDetailMoreMenu(tester);
        expect(find.text('刷新'), findsOneWidget, reason: '详情页菜单应包含刷新');
        expect(find.text('删除'), findsOneWidget, reason: '详情页菜单应包含删除');
        // 先关闭菜单
        await tester.tapAt(const Offset(10, 10));
        await tester.pumpAndSettle(const Duration(seconds: 1));
        await _visualPause(tester);

        // 7. 详情页点击刷新，进入 share dialog 用于更新，保存后正常开始AI分析
        await _openDetailMoreMenu(tester);
        await _tapTextOrFail(tester, '刷新');
        await _waitForShareDialogUpdateMode(tester, timeout: const Duration(seconds: 15));
        await _visualPause(tester);
        await _tapTextOrFail(tester, '保存更改');
        await _waitForDetailOrListAfterUpdate(tester, timeout: const Duration(seconds: 120));
        await _visualPause(tester);

        // 8. 详情页点击删除，回到列表且看不见该文章
        await _openDetailMoreMenu(tester);
        await _tapTextOrFail(tester, '删除');
        await _waitForWidgetOrFail(tester, find.text('确认删除'), const Duration(seconds: 10), reason: '应弹出确认删除对话框');
        await _visualPause(tester);
        await _tapTextOrFail(tester, '删除');
        await _waitForArticlesListWithAtLeastOneCard(tester, timeout: const Duration(seconds: 30));
        await _visualPause(tester);

        if (articleTitle != null && articleTitle.isNotEmpty) {
          expect(find.textContaining(articleTitle), findsNothing, reason: '删除后列表页不应再出现该文章');
        }

        logSuccess('文章收藏功能测试全部通过！');
      } catch (e, stackTrace) {
        logError('文章收藏功能测试失败', e, stackTrace);

        // 打印当前页面信息用于调试
        await _debugCurrentPage(tester);

        // 继续执行，不要让一个测试失败影响整体
        rethrow;
      }
    });
  });
}

/// 等待Widget出现
Future<bool> _waitForWidget(WidgetTester tester, Finder finder, Duration timeout) async {
  final endTime = DateTime.now().add(timeout);

  while (DateTime.now().isBefore(endTime)) {
    if (tester.any(finder)) {
      return true;
    }
    await tester.pump(const Duration(milliseconds: 100));
  }

  return false;
}

Future<void> _waitForWidgetOrFail(
  WidgetTester tester,
  Finder finder,
  Duration timeout, {
  required String reason,
}) async {
  final ok = await _waitForWidget(tester, finder, timeout);
  expect(ok, isTrue, reason: reason);
}

Future<void> _tapTextOrFail(WidgetTester tester, String text) async {
  final target = find.text(text);
  expect(target, findsWidgets, reason: '应该能找到 "$text"');
  await tester.tap(target.first);
  await tester.pumpAndSettle(const Duration(seconds: 1));
  await _visualPause(tester);
}

Future<void> _waitForShareDialog(WidgetTester tester, {required Duration timeout}) async {
  // ShareDialog 的标题在中文下是“保存链接”（ui.saveLink），底部按钮是“保存”（ui.save）
  final endTime = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(endTime)) {
    if (tester.any(find.text('保存链接')) && tester.any(find.text('保存'))) return;
    await tester.pump(const Duration(milliseconds: 200));
  }
  fail('未在超时时间内进入保存链接页面');
}

Future<void> _waitForShareDialogUpdateMode(WidgetTester tester, {required Duration timeout}) async {
  final endTime = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(endTime)) {
    if (tester.any(find.text('更新文章')) && tester.any(find.text('保存更改'))) return;
    await tester.pump(const Duration(milliseconds: 200));
  }
  fail('未在超时时间内进入更新文章页面');
}

Future<void> _waitForArticlesListWithAtLeastOneCard(WidgetTester tester, {required Duration timeout}) async {
  final endTime = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(endTime)) {
    await tester.pump(const Duration(milliseconds: 300));
    if (tester.any(find.byType(ArticleCard))) {
      return;
    }
  }
  fail('未在超时时间内看到文章列表项');
}

String? _tryExtractTitleFromFirstCard(WidgetTester tester) {
  final card = find.byType(ArticleCard).first;
  final texts = find.descendant(of: card, matching: find.byType(Text));
  for (final element in texts.evaluate()) {
    final widget = element.widget;
    if (widget is! Text) continue;
    final data = widget.data?.trim();
    if (data == null || data.isEmpty) continue;
    if (data == '加载失败') continue;
    // 跳过域名/日期等明显非标题内容
    if (data.contains('.') && data.length <= 20) continue;
    if (RegExp(r'^\d{2}-\d{2}$').hasMatch(data)) continue;
    return data.length > 12 ? data.substring(0, 12) : data;
  }
  return null;
}

Future<void> _toggleFavoriteInFirstCard(WidgetTester tester) async {
  final card = find.byType(ArticleCard).first;
  final favBorder = find.descendant(of: card, matching: find.byIcon(Icons.favorite_border));
  final favFilled = find.descendant(of: card, matching: find.byIcon(Icons.favorite));

  if (tester.any(favBorder)) {
    await tester.tap(favBorder.first);
    await tester.pumpAndSettle(const Duration(seconds: 1));
    await _visualPause(tester);
    expect(find.descendant(of: card, matching: find.byIcon(Icons.favorite)), findsOneWidget);
    return;
  }
  if (tester.any(favFilled)) {
    await tester.tap(favFilled.first);
    await tester.pumpAndSettle(const Duration(seconds: 1));
    await _visualPause(tester);
    expect(find.descendant(of: card, matching: find.byIcon(Icons.favorite_border)), findsOneWidget);
    return;
  }
  fail('未找到收藏按钮（favorite / favorite_border）');
}

Future<void> _searchArticleAndAssertResult(WidgetTester tester, {required String? query}) async {
  final effectiveQuery = (query ?? '').trim();
  if (effectiveQuery.isEmpty) {
    logInfo('未能提取稳定标题，跳过搜索断言（仅验证搜索栏可打开）');
  }

  // 打开搜索栏（文章页 AppBar 的搜索按钮是 FeatherIcons.search，运行时 IconData 可直接 byIcon）
  final searchIcon = find.byIcon(FeatherIcons.search);
  expect(searchIcon, findsWidgets, reason: '文章页应有搜索按钮');
  await tester.tap(searchIcon.first);
  await tester.pumpAndSettle(const Duration(seconds: 2));
  await _visualPause(tester);

  final searchBar = find.byType(GenericSearchBar);
  expect(searchBar, findsWidgets, reason: '打开搜索后应出现搜索栏组件');

  final field = find.descendant(of: searchBar.first, matching: find.byType(TextField));
  expect(field, findsWidgets, reason: '搜索栏展开后应有输入框');

  if (effectiveQuery.isNotEmpty) {
    await tester.enterText(field.first, effectiveQuery);
    await tester.pumpAndSettle(const Duration(seconds: 1));
    await _visualPause(tester);

    final doSearch = find.descendant(of: searchBar.first, matching: find.byIcon(Icons.search));
    expect(doSearch, findsWidgets, reason: '搜索栏应有执行搜索按钮');
    await tester.tap(doSearch.last);
    await tester.pumpAndSettle(const Duration(seconds: 2));
    await _visualPause(tester);

    expect(find.textContaining(effectiveQuery), findsWidgets, reason: '搜索结果应包含查询关键词');
  }

  // 清空搜索
  await tester.enterText(field.first, '');
  await tester.pumpAndSettle(const Duration(seconds: 1));
  await _visualPause(tester);
}

Future<void> _openFirstArticleDetail(WidgetTester tester) async {
  final card = find.byType(ArticleCard).first;
  final tappable = find.descendant(of: card, matching: find.byType(InkWell));
  expect(tappable, findsWidgets, reason: '文章卡片应可点击进入详情');
  await tester.tap(tappable.first);
  await tester.pumpAndSettle(const Duration(seconds: 1));
  await _visualPause(tester);
}

Future<void> _openDetailMoreMenu(WidgetTester tester) async {
  final menuButton = find.byIcon(Icons.more_horiz);
  expect(menuButton, findsWidgets, reason: '详情页应有更多菜单按钮');
  await tester.tap(menuButton.first);
  await tester.pumpAndSettle(const Duration(seconds: 1));
  await _visualPause(tester);
}

Future<void> _openOriginalTabAndAssertVisible(WidgetTester tester) async {
  final originalTab = find.text('原文');
  expect(originalTab, findsWidgets, reason: '详情页应有“原文”标签');
  await tester.tap(originalTab.first);
  await tester.pumpAndSettle(const Duration(seconds: 2));
  await _visualPause(tester);

  // OriginalContentTab 会显示 MarkdownBody / Html / 或空态文案
  final hasMarkdown = tester.any(find.byType(MarkdownBody));
  final hasHtml = tester.any(find.byType(Html));
  final hasEmpty1 = tester.any(find.text('无法加载原文内容'));
  final hasEmpty2 = tester.any(find.text('尚未生成Markdown内容'));

  expect(hasMarkdown || hasHtml || hasEmpty1 || hasEmpty2, isTrue, reason: '点击“原文”后应能看到原文内容区域（Markdown/HTML/空态）');
}

Future<void> _visualPause(WidgetTester tester, {Duration duration = const Duration(milliseconds: 600)}) async {
  // 让肉眼能看清每个步骤；使用 pump 而不是 sleep，避免阻塞测试框架。
  await tester.pump(duration);
}

Future<void> _waitForDetailOrListAfterUpdate(WidgetTester tester, {required Duration timeout}) async {
  final endTime = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(endTime)) {
    await tester.pump(const Duration(milliseconds: 300));
    // 更新保存后一般回到详情页（有 more_horiz），或者回到列表（有 InkWell 卡片）
    if (tester.any(find.byIcon(Icons.more_horiz)) || tester.any(find.byType(ArticleCard))) {
      return;
    }
  }
  fail('更新保存后未回到详情/列表页面');
}

/// 调试当前页面信息
Future<void> _debugCurrentPage(WidgetTester tester) async {
  logInfo('=== 调试当前页面 ===');

  // 查找所有Scaffold
  final scaffolds = find.byType(Scaffold);
  logInfo('Scaffold数量: ${scaffolds.evaluate().length}');

  // 查找所有文本
  final texts = find.byType(Text);
  logInfo('Text组件数量: ${texts.evaluate().length}');

  // 打印前10个文本
  for (int i = 0; i < 10 && i < texts.evaluate().length; i++) {
    try {
      final textWidget = tester.widget<Text>(texts.at(i));
      logInfo('Text[$i]: ${textWidget.data}');
    } catch (e) {
      logInfo('Text[$i]: 无法读取');
    }
  }

  // 查找所有按钮
  final buttons = find.byType(ElevatedButton);
  logInfo('ElevatedButton数量: ${buttons.evaluate().length}');

  final textButtons = find.byType(TextButton);
  logInfo('TextButton数量: ${textButtons.evaluate().length}');

  final iconButtons = find.byType(IconButton);
  logInfo('IconButton数量: ${iconButtons.evaluate().length}');

  logInfo('=== 调试结束 ===');
}

// 辅助方法
void logInfo(String message) {
  print('[INFO] $message');
}

void logSuccess(String message) {
  print('[SUCCESS] ✓ $message');
}

void logError(String message, dynamic error, StackTrace? stackTrace) {
  print('[ERROR] ✗ $message');
  print('Error: $error');
  if (stackTrace != null) {
    print('StackTrace: $stackTrace');
  }
}
