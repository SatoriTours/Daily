import 'dart:io';
import 'dart:isolate';

import 'package:flutter/services.dart';
import 'package:flutter_archive/flutter_archive.dart';
import 'package:path/path.dart' as path;

import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';

class BackupService {
  // 单例模式
  BackupService._();
  static final BackupService _instance = BackupService._();
  static BackupService get i => _instance;

  // 常量定义
  static const int _productionBackupInterval = 6;
  static const int _developmentBackupInterval = 24;

  // Getters
  String get backupDir => SettingRepository.getSetting(SettingService.backupDirKey);
  File get backupTimeFile => File(path.join(backupDir, 'backup_time.txt'));
  int get _backupInterval => isProduction ? _productionBackupInterval : _developmentBackupInterval;

  // 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] BackupService");
    checkAndBackup();
  }

  // 检查并执行备份
  Future<void> checkAndBackup({bool immediateBackup = false}) async {
    if (backupDir.isEmpty) return;
    try {
      logger.i("准备备份应用");
      final lastBackupTime = await _getLastBackupTime();
      final backupTimeDifference = DateTime.now().difference(lastBackupTime).inHours;

      if (backupTimeDifference >= _backupInterval || immediateBackup) {
        logger.i("开始备份应用");
        // 使用Isolate执行备份
        await _startBackup();
      } else {
        final remainingHours = _backupInterval - backupTimeDifference;
        logger.i("上次备份时间 $lastBackupTime, 备份间隔为 $_backupInterval 小时, 离下次备份还差: $remainingHours 小时");
      }
    } catch (e) {
      logger.e("备份应用过程中发生错误: $e");
    }
  }

  Future<void> _startBackup() async {
    await _performBackup();
    await _updateLastBackupTime(DateTime.now());
  }

  // 获取上次备份时间
  Future<DateTime> _getLastBackupTime() async {
    if (await backupTimeFile.exists()) {
      final content = await backupTimeFile.readAsString();
      return DateTime.parse(content);
    }
    return DateTime.fromMillisecondsSinceEpoch(0);
  }

  // 执行备份
  Future<void> _performBackup() async {
    final timestamp = DateTime.now().toIso8601String().replaceAll(RegExp('[:.]+'), '-');
    final backupFolder = path.join(backupDir, 'daily_satori_backup_$timestamp');
    await Directory(backupFolder).create(recursive: true);

    final backupTasks = [
      _compressDirectory(FileService.i.imagesBasePath, path.join(backupFolder, 'images.zip')),
      _compressDirectory(FileService.i.diaryImagesBasePath, path.join(backupFolder, 'diary_images.zip')),
      _compressDirectory(FileService.i.dbPath, path.join(backupFolder, 'objectbox.zip')),
    ];

    await Future.wait(backupTasks);
    logger.i("完成了文件的备份: $backupFolder");
  }

  // 压缩目录
  Future<void> _compressDirectory(String sourceDir, String targetPath) async {
    try {
      final sourceDirectory = Directory(sourceDir);

      if (await sourceDirectory.exists()) {
        // 使用 Isolate 执行压缩任务
        final rootIsolateToken = RootIsolateToken.instance!;
        await Isolate.run(() async {
          BackgroundIsolateBinaryMessenger.ensureInitialized(rootIsolateToken);
          final targetFile = File(targetPath);
          await ZipFile.createFromDirectory(sourceDir: sourceDirectory, zipFile: targetFile, recurseSubDirs: true);
        });
        logger.i("成功压缩目录 $sourceDir 到 $targetPath");
      } else {
        logger.w("源目录不存在: $sourceDir");
      }
    } catch (e) {
      logger.e("压缩目录时出错: $e");
      rethrow;
    }
  }

  // 更新备份时间
  Future<void> _updateLastBackupTime(DateTime time) async {
    await backupTimeFile.writeAsString(time.toIso8601String());
  }
}
