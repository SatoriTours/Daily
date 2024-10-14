import 'dart:io';
import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/global.dart';
import 'package:path/path.dart';
import 'package:daily_satori/app/services/settings_service.dart';
import 'package:path/path.dart' as path;

class BackupService {
  BackupService._privateConstructor();
  static final BackupService _instance = BackupService._privateConstructor();
  static BackupService get instance => _instance;

  Future<void> init() async {
    checkAndBackup();
  }

  String get backupDir => SettingsService.instance.getSetting(SettingsService.backupDirKey);
  File get backupTimeFile => File(path.join(backupDir, 'backup_time.txt'));

  Future<void> checkAndBackup() async {
    if (backupDir.isEmpty) {
      return; // 如果备份目录为空，不启动备份功能
    }

    logger.i("准备备份应用");

    DateTime lastBackupTime = await _getLastBackupTime();
    int backupTimeDifference = DateTime.now().difference(lastBackupTime).inHours;
    if (backupTimeDifference >= 6) {
      logger.i("开始备份应用");
      await _performBackup(backupDir);
      await _updateLastBackupTime(DateTime.now());
    } else {
      logger.i("上次备份时间 $lastBackupTime, 离下次备份还差: ${6 - backupTimeDifference} 小时");
    }
  }

  Future<DateTime> _getLastBackupTime() async {
    final file = backupTimeFile;
    if (await file.exists()) {
      String content = await file.readAsString();
      return DateTime.parse(content);
    }
    return DateTime.fromMillisecondsSinceEpoch(0); // 如果没有记录，返回一个很早的时间
  }

  Future<void> _performBackup(String backupDir) async {
    String imagesDir = FileService.instance.imagesBasePath;
    String screenshotsDir = FileService.instance.screenshotsBasePath;
    String databaseFile = await DatabaseService.instance.dbFilePath();

    String timestamp = DateTime.now().toIso8601String().replaceAll(':', '-').replaceAll('.', '-');
    String backupFolder = join(backupDir, 'daily_satori_backup_$timestamp');
    await Directory(backupFolder).create(recursive: true);

    await _copyDirectory(Directory(imagesDir), Directory(join(backupFolder, 'images')));
    await _copyDirectory(Directory(screenshotsDir), Directory(join(backupFolder, 'screenshots')));
    await File(databaseFile).copy(join(backupFolder, 'database.db'));
    logger.i("完成了文件的备份: $backupFolder");
  }

  Future<void> _copyDirectory(Directory source, Directory destination) async {
    if (await source.exists()) {
      await destination.create(recursive: true);
      await for (var entity in source.list()) {
        if (entity is File) {
          await entity.copy(join(destination.path, basename(entity.path)));
        } else if (entity is Directory) {
          await _copyDirectory(entity, Directory(join(destination.path, basename(entity.path))));
        }
      }
    }
  }

  Future<void> _updateLastBackupTime(DateTime time) async {
    final file = backupTimeFile;
    await file.writeAsString(time.toIso8601String());
  }
}
