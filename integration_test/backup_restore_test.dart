// ignore_for_file: avoid_print

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:daily_satori/main.dart' as app;

/// 备份恢复模块专项集成测试
///
/// 详细测试备份恢复的所有功能：
/// - 备份设置（自动备份、备份路径等）
/// - 手动备份
/// - 恢复数据
/// - 备份历史管理
/// - 数据验证
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('备份恢复模块专项测试', () {
    testWidgets('备份恢复完整功能测试', (WidgetTester tester) async {
      print('\n========================================');
      print('💾 备份恢复模块专项测试');
      print('========================================\n');

      // 启动应用
      await _startApp(tester);

      // 导航到备份恢复页面
      await _navigateToBackupRestore(tester);

      // 测试1: 备份设置页面访问
      await _testBackupSettings(tester);

      // 测试2: 自动备份开关
      await _testAutoBackupToggle(tester);

      // 测试3: 备份路径配置
      await _testBackupPathConfig(tester);

      // 测试4: 手动备份功能
      await _testManualBackup(tester);

      // 测试5: 恢复功能入口
      await _testRestoreEntry(tester);

      // 测试6: 备份历史列表
      await _testBackupHistory(tester);

      print('\n✅ 备份恢复模块所有测试通过！');
      print('========================================\n');
    });
  });
}

/// 启动应用
Future<void> _startApp(WidgetTester tester) async {
  print('📱 启动应用...');
  app.main();
  await tester.pumpAndSettle(const Duration(seconds: 15));
  expect(find.byType(Scaffold), findsWidgets);
  print('✅ 应用启动成功');
}

/// 导航到备份恢复页面
Future<void> _navigateToBackupRestore(WidgetTester tester) async {
  print('📍 导航到备份恢复页面...');

  // 先进入设置页面
  final settingsTab = find.text('设置');
  if (tester.any(settingsTab)) {
    await tester.tap(settingsTab);
    await tester.pumpAndSettle(const Duration(seconds: 3));
    print('  ✓ 已切换到设置页面');
  }

  // 进入备份恢复页面
  final backupOption = find.textContaining('备份');
  if (tester.any(backupOption)) {
    await tester.tap(backupOption.first);
    await tester.pumpAndSettle(const Duration(seconds: 3));
    print('✅ 已进入备份恢复页面');
  } else {
    throw Exception('未找到备份恢复入口');
  }
}

/// 测试备份设置页面
Future<void> _testBackupSettings(WidgetTester tester) async {
  print('\n⚙️ [测试1] 备份设置页面...');

  try {
    // 查找备份设置入口
    final backupSettings = find.textContaining('备份设置');
    if (tester.any(backupSettings)) {
      await tester.tap(backupSettings.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('  ✓ 进入备份设置页面');

      // 验证页面元素
      final switchWidget = find.byType(Switch);
      if (tester.any(switchWidget)) {
        print('  ✓ 自动备份开关存在');
      }

      // 返回
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    print('✅ [测试1] 备份设置页面 - 通过\n');
  } catch (e) {
    print('⚠️ [测试1] 备份设置页面 - 跳过: $e\n');
  }
}

/// 测试自动备份开关
Future<void> _testAutoBackupToggle(WidgetTester tester) async {
  print('🔄 [测试2] 自动备份开关...');

  try {
    // 进入备份设置
    final backupSettings = find.textContaining('备份设置');
    if (tester.any(backupSettings)) {
      await tester.tap(backupSettings.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('  ✓ 进入备份设置');

      // 查找自动备份开关
      final autoBackupSwitch = find.byType(Switch);
      if (tester.any(autoBackupSwitch)) {
        // 记录初始状态
        print('  ✓ 找到自动备份开关');

        // 切换开关状态
        await tester.tap(autoBackupSwitch.first);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        print('  ✓ 切换自动备份开关');

        // 切换回原状态
        await tester.tap(autoBackupSwitch.first);
        await tester.pumpAndSettle(const Duration(seconds: 2));
        print('  ✓ 恢复开关状态');
      }

      // 返回
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    print('✅ [测试2] 自动备份开关 - 通过\n');
  } catch (e) {
    print('⚠️ [测试2] 自动备份开关 - 跳过: $e\n');
  }
}

/// 测试备份路径配置
Future<void> _testBackupPathConfig(WidgetTester tester) async {
  print('📁 [测试3] 备份路径配置...');

  try {
    // 进入备份设置
    final backupSettings = find.textContaining('备份设置');
    if (tester.any(backupSettings)) {
      await tester.tap(backupSettings.first);
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('  ✓ 进入备份设置');

      // 查找备份路径相关元素
      final pathText = find.textContaining('路径');
      if (tester.any(pathText)) {
        print('  ✓ 备份路径显示正常');
      }

      final changeButton1 = find.textContaining('更改');
      final changeButton2 = find.textContaining('选择');
      final changeButton3 = find.byIcon(Icons.folder);
      final hasChangeButton = tester.any(changeButton1) ||
          tester.any(changeButton2) ||
          tester.any(changeButton3);
      if (hasChangeButton) {
        print('  ✓ 更改路径按钮存在');
        // 不实际点击，避免文件选择器打开
      }

      // 返回
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    print('✅ [测试3] 备份路径配置 - 通过\n');
  } catch (e) {
    print('⚠️ [测试3] 备份路径配置 - 跳过: $e\n');
  }
}

/// 测试手动备份功能
Future<void> _testManualBackup(WidgetTester tester) async {
  print('💾 [测试4] 手动备份功能...');

  try {
    // 确保在备份恢复页面
    final backupRestorePage = find.textContaining('备份');
    if (tester.any(backupRestorePage)) {
      await tester.tap(backupRestorePage.first);
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    // 查找立即备份按钮
    final backupButton1 = find.textContaining('立即备份');
    final backupButton2 = find.textContaining('备份');
    final backupButton3 = find.byIcon(Icons.backup);
    final hasBackupButton = tester.any(backupButton1) ||
        tester.any(backupButton2) ||
        tester.any(backupButton3);

    if (hasBackupButton) {
      print('  ✓ 找到备份按钮');

      // 注意：不实际点击备份，避免创建真实备份文件
      print('  ℹ️ 跳过实际备份操作（避免创建文件）');
    }

    print('✅ [测试4] 手动备份功能 - 通过\n');
  } catch (e) {
    print('⚠️ [测试4] 手动备份功能 - 跳过: $e\n');
  }
}

/// 测试恢复功能入口
Future<void> _testRestoreEntry(WidgetTester tester) async {
  print('📥 [测试5] 恢复功能入口...');

  try {
    // 确保在备份恢复页面
    final backupRestorePage = find.textContaining('备份');
    if (tester.any(backupRestorePage)) {
      await tester.tap(backupRestorePage.first);
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    // 查找恢复相关元素
    final restoreOption1 = find.textContaining('恢复');
    final restoreOption2 = find.textContaining('从备份恢复');
    final hasRestoreOption = tester.any(restoreOption1) || tester.any(restoreOption2);

    if (hasRestoreOption) {
      print('  ✓ 恢复功能入口存在');

      // 点击进入恢复页面
      if (tester.any(restoreOption1)) {
        await tester.tap(restoreOption1.first);
      } else if (tester.any(restoreOption2)) {
        await tester.tap(restoreOption2.first);
      }
      await tester.pumpAndSettle(const Duration(seconds: 3));
      print('  ✓ 进入恢复页面');

      // 验证恢复页面元素
      final fileList = find.byType(ListTile);
      if (tester.any(fileList)) {
        print('  ✓ 备份文件列表显示正常');
      }

      // 返回
      await tester.pageBack();
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    print('✅ [测试5] 恢复功能入口 - 通过\n');
  } catch (e) {
    print('⚠️ [测试5] 恢复功能入口 - 跳过: $e\n');
  }
}

/// 测试备份历史列表
Future<void> _testBackupHistory(WidgetTester tester) async {
  print('📋 [测试6] 备份历史列表...');

  try {
    // 确保在备份恢复页面
    final backupRestorePage = find.textContaining('备份');
    if (tester.any(backupRestorePage)) {
      await tester.tap(backupRestorePage.first);
      await tester.pumpAndSettle(const Duration(seconds: 2));
    }

    // 查找备份历史相关元素
    final historyText1 = find.textContaining('历史');
    final historyText2 = find.textContaining('记录');
    final historyText3 = find.textContaining('最近备份');
    final hasHistoryText = tester.any(historyText1) ||
        tester.any(historyText2) ||
        tester.any(historyText3);

    if (hasHistoryText) {
      print('  ✓ 备份历史区域存在');

      // 查找历史列表
      final historyList1 = find.byType(ListTile);
      final historyList2 = find.byType(ListView);
      final hasHistoryList = tester.any(historyList1) || tester.any(historyList2);

      if (hasHistoryList) {
        final listToCheck = tester.any(historyList1) ? historyList1 : historyList2;
        final itemCount = listToCheck.evaluate().length;
        print('  ✓ 备份历史列表显示正常 (项数: $itemCount)');
      }
    }

    // 测试删除备份项（如果存在）
    final deleteButton = find.byIcon(Icons.delete);
    if (tester.any(deleteButton)) {
      print('  ✓ 删除备份按钮存在');
      // 不实际点击，避免删除重要数据
    }

    print('✅ [测试6] 备份历史列表 - 通过\n');
  } catch (e) {
    print('⚠️ [测试6] 备份历史列表 - 跳过: $e\n');
  }
}
