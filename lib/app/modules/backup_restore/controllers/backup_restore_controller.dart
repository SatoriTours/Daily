import 'dart:io';

import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/settings_service.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter_archive/flutter_archive.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class BackupRestoreController extends MyBaseController {
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
    String backupDir = SettingsService.i.getSetting(SettingsService.backupDirKey);
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
    String formattedDateStr = dateStr!
        .replaceAllMapped(RegExp(r'T(\d{2})-(\d{2})-(\d{2})'), (match) => 'T${match[1]}:${match[2]}:${match[3]}');
    DateTime dateTime = DateTime.parse(formattedDateStr);
    return DateFormat('yyyy年MM月dd日 HH:mm:ss').format(dateTime);
  }

  Future<bool> restoreBackup() async {
    String backupName = backupList[selectedBackupIndex.value];
    String backupDir = SettingsService.i.getSetting(SettingsService.backupDirKey);
    String backupFolder = path.join(backupDir, 'daily_satori_backup_$backupName');

    final imagesFile = File(path.join(backupFolder, 'images.zip'));
    final screenshotsFile = File(path.join(backupFolder, 'screenshots.zip'));
    final databaseFile = File(path.join(backupFolder, 'objectbox.zip'));

    // 应用程序文档目录
    // final directory = await getApplicationDocumentsDirectory();
    // String appDocDir = path.join(directory.path, "backup_store");
    String appDocDir = (await getApplicationDocumentsDirectory()).path;

    if (await imagesFile.exists() && await screenshotsFile.exists() && await databaseFile.exists()) {
      logger.i("恢复备份 images 目录");
      await ZipFile.extractToDirectory(zipFile: imagesFile, destinationDir: Directory(path.join(appDocDir, 'images')));
      logger.i("恢复备份 screenshots 目录");
      await ZipFile.extractToDirectory(
          zipFile: screenshotsFile, destinationDir: Directory(path.join(appDocDir, 'screenshots')));
      logger.i("恢复备份数据库文件");
      await ZipFile.extractToDirectory(
          zipFile: databaseFile, destinationDir: Directory(path.join(appDocDir, ObjectboxService.dbDir)));
      logger.i("恢复备份完成: $backupFolder => $appDocDir");
      return true;
    } else {
      logger.e("备份文件不存在, 无法恢复");
      return false;
    }
  }
}
