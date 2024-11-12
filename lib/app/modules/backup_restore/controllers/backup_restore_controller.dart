import 'dart:io';

import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/app/services/settings_service.dart';
import 'package:daily_satori/global.dart';
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

  Future<void> restoreBackup() async {
    String backupName = backupList[selectedBackupIndex.value];
    String backupDir = SettingsService.i.getSetting(SettingsService.backupDirKey);
    String backupFolder = path.join(backupDir, 'daily_satori_backup_$backupName');

    final imagesDir = Directory(path.join(backupFolder, 'images'));
    final screenshotsDir = Directory(path.join(backupFolder, 'screenshots'));
    final databaseFile = File(path.join(backupFolder, DBService.dbFileName));

    // 应用程序文档目录
    // final directory = await getApplicationDocumentsDirectory();
    // String appDocDir = path.join(directory.path, "backup_store");
    String appDocDir = (await getApplicationDocumentsDirectory()).path;

    // 拷贝 images
    if (await imagesDir.exists()) {
      logger.i("恢复备份 images 目录");
      await _copyDirectory(imagesDir, Directory(path.join(appDocDir, 'images')));
    }

    // 拷贝 screenshots
    if (await screenshotsDir.exists()) {
      logger.i("恢复备份 screenshots 目录");
      await _copyDirectory(screenshotsDir, Directory(path.join(appDocDir, 'screenshots')));
    }

    // 拷贝数据库文件
    if (await databaseFile.exists()) {
      logger.i("恢复备份 数据库文件");
      await databaseFile.copy(path.join(appDocDir, DBService.dbFileName));
    }

    logger.i("恢复备份完成: $backupFolder => $appDocDir");
  }

  Future<void> _copyDirectory(Directory source, Directory destination) async {
    if (await source.exists()) {
      await destination.create(recursive: true);
      await for (var entity in source.list()) {
        if (entity is File) {
          await entity.copy(path.join(destination.path, path.basename(entity.path)));
        } else if (entity is Directory) {
          await _copyDirectory(entity, Directory(path.join(destination.path, path.basename(entity.path))));
        }
      }
    }
  }
}
