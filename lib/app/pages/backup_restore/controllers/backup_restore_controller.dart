import 'dart:io';

import 'package:daily_satori/app_exports.dart';
import 'package:intl/intl.dart';
import 'package:path/path.dart' as p;

class BackupRestoreController extends BaseController {
  var selectedBackupIndex = 0.obs;
  var backupList = <String>[].obs;

  @override
  void onInit() {
    super.onInit();
    reloadBackupList();
  }

  Future<void> reloadBackupList() async {
    backupList.clear();
    logger.i("开始加载备份列表");
    String backupDir = SettingRepository.i.getSetting(SettingService.backupDirKey);
    final directory = Directory(backupDir);

    if (await directory.exists()) {
      // 仅收集符合命名规范的备份目录，并按目录名倒序
      const prefix = 'daily_satori_backup_';
      final entities = await directory
          .list()
          .where((e) => e is Directory && p.basename(e.path).startsWith(prefix))
          .toList();

      entities.sort((a, b) => p.basename(b.path).compareTo(p.basename(a.path))); // 根据目录名倒序排序

      for (var entity in entities) {
        final base = p.basename(entity.path);
        final name = base.substring(prefix.length); // 提取时间戳部分
        backupList.add(name);
      }
    }

    logger.i("加载备份列表完成, 获取 ${backupList.length} 个备份");
    // 调整默认选中项
    selectedBackupIndex.value = backupList.isNotEmpty ? 0 : -1;
  }

  String? getBackupTime(String backupName) {
    try {
      final regExp = RegExp(r'(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2})');
      final match = regExp.firstMatch(backupName);
      final dateStr = match?.group(1);
      if (dateStr == null) return null;

      final formattedDateStr = dateStr.replaceAllMapped(
        RegExp(r'T(\d{2})-(\d{2})-(\d{2})'),
        (m) => 'T${m[1]}:${m[2]}:${m[3]}',
      );
      final dateTime = DateTime.parse(formattedDateStr);
      return DateFormat('yyyy年MM月dd日 HH:mm:ss').format(dateTime);
    } catch (_) {
      return null;
    }
  }

  Future<bool> restoreBackup() async {
    if (selectedBackupIndex.value < 0 || selectedBackupIndex.value >= backupList.length) {
      return false;
    }
    // 调用 BackupService 的恢复方法
    return await BackupService.i.restoreBackup(backupList[selectedBackupIndex.value]);
  }
}
