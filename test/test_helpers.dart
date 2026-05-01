import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

/// 测试辅助工具类
class TestHelpers {
  /// 等待应用加载完成
  static Future<void> waitForAppLoad(
    WidgetTester tester, {
    int seconds = 3,
  }) async {
    await tester.pumpAndSettle(Duration(seconds: seconds));
  }

  /// 查找并点击tab
  static Future<bool> tapTab(WidgetTester tester, String tabName) async {
    final tab = find.text(tabName).first;
    if (tester.any(tab)) {
      await tester.tap(tab);
      await tester.pumpAndSettle();
      return true;
    }
    return false;
  }

  /// 查找并点击图标按钮
  static Future<bool> tapIconButton(WidgetTester tester, IconData icon) async {
    final button = find.byIcon(icon).first;
    if (tester.any(button)) {
      await tester.tap(button);
      await tester.pumpAndSettle();
      return true;
    }
    return false;
  }

  /// 查找并点击文本按钮
  static Future<bool> tapTextButton(WidgetTester tester, String text) async {
    final button = find.text(text).first;
    if (tester.any(button)) {
      await tester.tap(button);
      await tester.pumpAndSettle();
      return true;
    }
    return false;
  }

  /// 在文本字段中输入文本
  static Future<bool> enterText(
    WidgetTester tester,
    String text, {
    bool clearFirst = false,
  }) async {
    final field = find.byType(TextField).first;
    if (tester.any(field)) {
      await tester.tap(field);
      if (clearFirst) {
        await tester.enterText(field, '');
      }
      await tester.enterText(field, text);
      await tester.pumpAndSettle();
      return true;
    }
    return false;
  }

  /// 查找并点击列表项
  static Future<bool> tapListItem(WidgetTester tester, {int index = 0}) async {
    final items = find.byType(ListTile);
    if (tester.widgetList(items).length > index) {
      await tester.tap(items.at(index));
      await tester.pumpAndSettle();
      return true;
    }
    return false;
  }

  /// 查找并点击FloatingActionButton
  static Future<bool> tapFloatingActionButton(WidgetTester tester) async {
    final fab = find.byType(FloatingActionButton).first;
    if (tester.any(fab)) {
      await tester.tap(fab);
      await tester.pumpAndSettle();
      return true;
    }
    return false;
  }

  /// 验证当前页面包含特定文本
  static bool verifyPageContains(WidgetTester tester, String text) {
    return tester.any(find.text(text));
  }

  /// 打印调试信息
  static void logDebug(String message) {
    // 注意：测试环境中使用print，生产环境中应该使用日志框架
    // ignore: avoid_print
    print('[TEST DEBUG] $message');
  }
}
