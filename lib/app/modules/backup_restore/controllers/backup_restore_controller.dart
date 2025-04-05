import 'dart:io';

import 'package:daily_satori/app_exports.dart';
import 'package:intl/intl.dart';

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
    String backupDir = SettingRepository.getSetting(SettingService.backupDirKey);
    final directory = Directory(backupDir);

    if (await directory.exists()) {
      var entities = await directory.list().toList();
      entities.sort((a, b) => b.path.compareTo(a.path)); // 根据文件名倒序排序
      for (var entity in entities) {
        logger.i("得等到目录 ${entity.path}");
        if (entity is Directory && entity.path.contains('daily_satori_backup_')) {
          String dirName = entity.path.split('_').last;
          backupList.add(dirName);
        }
      }
    }

    logger.i("加载备份列表完成, 获取 ${backupList.length} 个备份");
  }

  String? getBackupTime(String backupName) {
    RegExp regExp = RegExp(r'(\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2})');
    Match? match = regExp.firstMatch(backupName);
    String? dateStr = match?.group(1);
    String formattedDateStr = dateStr!.replaceAllMapped(
      RegExp(r'T(\d{2})-(\d{2})-(\d{2})'),
      (match) => 'T${match[1]}:${match[2]}:${match[3]}',
    );
    DateTime dateTime = DateTime.parse(formattedDateStr);
    return DateFormat('yyyy年MM月dd日 HH:mm:ss').format(dateTime);
  }

  Future<bool> restoreBackup() async {
    if (selectedBackupIndex.value < 0 || selectedBackupIndex.value >= backupList.length) {
      return false;
    }
    // 调用 BackupService 的恢复方法
    return await BackupService.i.restoreBackup(backupList[selectedBackupIndex.value]);
  }
}
