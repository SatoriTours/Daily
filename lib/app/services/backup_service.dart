import 'dart:io';

import 'package:flutter_archive/flutter_archive.dart';
import 'package:path/path.dart' as path;

import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/global.dart';

class BackupService {
  BackupService._privateConstructor();
  static final BackupService _instance = BackupService._privateConstructor();
  static BackupService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] BackupService");
    checkAndBackup();
  }

  String get backupDir =>
      SettingService.i.getSetting(SettingService.backupDirKey);
  File get backupTimeFile => File(path.join(backupDir, 'backup_time.txt'));

  Future<void> checkAndBackup({bool immediateBackup = false}) async {
    if (backupDir.isEmpty) {
      return; // 如果备份目录为空，不启动备份功能
    }

    logger.i("准备备份应用");

    DateTime lastBackupTime = await _getLastBackupTime();
    int backupTimeDifference =
        DateTime.now().difference(lastBackupTime).inHours;
    // 根据环境设置不同的备份间隔
    int backupInterval = isProduction ? 6 : 24;
    if (backupTimeDifference >= backupInterval || immediateBackup) {
      logger.i("开始备份应用");
      await _performBackup(backupDir);
      await _updateLastBackupTime(DateTime.now());
    } else {
      logger.i(
          "上次备份时间 $lastBackupTime, 备份间隔为 $backupInterval 小时, 离下次备份还差: ${backupInterval - backupTimeDifference} 小时");
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
    String imagesDir = FileService.i.imagesBasePath;
    String screenshotsDir = FileService.i.screenshotsBasePath;
    String databaseDir = FileService.i.dbPath;

    String timestamp = DateTime.now()
        .toIso8601String()
        .replaceAll(':', '-')
        .replaceAll('.', '-');
    String backupFolder =
        path.join(backupDir, 'daily_satori_backup_$timestamp');
    await Directory(backupFolder).create(recursive: true);

    // await _copyDirectory(Directory(imagesDir), Directory(path.join(backupFolder, 'images')));
    // await _copyDirectory(Directory(screenshotsDir), Directory(path.join(backupFolder, 'screenshots')));
    await _compressDirectory(imagesDir, path.join(backupFolder, 'images.zip'));
    await _compressDirectory(
        screenshotsDir, path.join(backupFolder, 'screenshots.zip'));
    await _compressDirectory(
        databaseDir, path.join(backupFolder, 'objectbox.zip'));
    logger.i("完成了文件的备份: $backupFolder");
  }

  Future<void> _compressDirectory(String sourceDir, String targetPath) async {
    try {
      final sourceDirectory = Directory(sourceDir);
      final targetFile = File(targetPath);

      if (await sourceDirectory.exists()) {
        await ZipFile.createFromDirectory(
          sourceDir: sourceDirectory,
          zipFile: targetFile,
          recurseSubDirs: true,
        );
        logger.i("成功压缩目录 $sourceDir 到 $targetPath");
      } else {
        logger.w("源目录不存在: $sourceDir");
      }
    } catch (e) {
      logger.e("压缩目录时出错: $e");
      rethrow;
    }
  }

  Future<void> _copyDirectory(Directory source, Directory destination) async {
    if (await source.exists()) {
      await destination.create(recursive: true);
      await for (var entity in source.list()) {
        if (entity is File) {
          await entity
              .copy(path.join(destination.path, path.basename(entity.path)));
        } else if (entity is Directory) {
          await _copyDirectory(
              entity,
              Directory(
                  path.join(destination.path, path.basename(entity.path))));
        }
      }
    }
  }

  Future<void> _updateLastBackupTime(DateTime time) async {
    final file = backupTimeFile;
    await file.writeAsString(time.toIso8601String());
  }
}
