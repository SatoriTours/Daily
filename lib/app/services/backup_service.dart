import 'dart:io';
import 'dart:isolate';

import 'package:flutter/services.dart';
import 'package:flutter_archive/flutter_archive.dart';
import 'package:get/get.dart';
import 'package:path/path.dart' as path;
import 'package:archive/archive.dart' as archive;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';

/// 备份项配置类，定义备份内容和目标
class BackupItem {
  final String name; // 备份项名称
  final String sourcePath; // 源路径
  final String zipFileName; // 目标文件名

  const BackupItem({required this.name, required this.sourcePath, required this.zipFileName});
}

class BackupService {
  // 单例模式
  BackupService._();
  static final BackupService _instance = BackupService._();
  static BackupService get i => _instance;

  // 常量定义
  static const int _productionBackupInterval = 6;
  static const int _developmentBackupInterval = 24;

  // 备份进度监控
  final isBackingUp = false.obs;
  final backupProgress = 0.0.obs;

  // 备份项配置
  late final List<BackupItem> _backupItems;

  // 初始化备份项配置
  void _initBackupItems() {
    _backupItems = [
      BackupItem(name: "数据库", sourcePath: FileService.i.dbPath, zipFileName: 'objectbox.zip'),
      BackupItem(name: "网页图片", sourcePath: FileService.i.imagesBasePath, zipFileName: 'images.zip'),
      BackupItem(name: "日记图片", sourcePath: FileService.i.diaryImagesBasePath, zipFileName: 'diary_images.zip'),
    ];
  }

  // Getters
  String get backupDir => SettingRepository.getSetting(SettingService.backupDirKey);
  File get backupTimeFile => File(path.join(backupDir, 'backup_time.txt'));
  int get _backupInterval => AppInfoUtils.isProduction ? _productionBackupInterval : _developmentBackupInterval;
  List<BackupItem> get backupItems => _backupItems;

  // 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] BackupService");
    _initBackupItems();
    checkAndBackup();
  }

  // 检查并执行备份
  Future<bool> checkAndBackup({bool immediateBackup = false}) async {
    if (backupDir.isEmpty) return false;

    if (isBackingUp.value) {
      logger.w("已经有备份任务在进行中");
      return false;
    }

    // 非生产环境不执行备份
    if (!AppInfoUtils.isProduction) return false;

    isBackingUp.value = true;
    backupProgress.value = 0.0;

    try {
      logger.i("准备备份应用");
      final lastBackupTime = await _getLastBackupTime();
      final backupTimeDifference = DateTime.now().difference(lastBackupTime).inHours;

      if (backupTimeDifference >= _backupInterval || immediateBackup) {
        logger.i("开始备份应用");
        // 执行备份
        await _startBackup();
        return true;
      } else {
        final remainingHours = _backupInterval - backupTimeDifference;
        logger.i("上次备份时间 $lastBackupTime, 备份间隔为 $_backupInterval 小时, 离下次备份还差: $remainingHours 小时");
        return false;
      }
    } catch (e) {
      logger.e("备份应用过程中发生错误: $e");
      return false;
    } finally {
      isBackingUp.value = false;
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

    backupProgress.value = 0.1;

    final progressPerItem = 0.9 / _backupItems.length;
    double currentProgress = 0.1;

    // 按配置进行备份
    for (final item in _backupItems) {
      logger.i("开始备份${item.name}");
      await _compressDirectory(item.sourcePath, path.join(backupFolder, item.zipFileName));
      currentProgress += progressPerItem;
      backupProgress.value = currentProgress;
    }

    backupProgress.value = 1.0;
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

  // 从备份恢复文件
  Future<bool> restoreBackup(String backupName) async {
    String backupFolder = path.join(backupDir, 'daily_satori_backup_$backupName');
    String appDocDir = (await getApplicationDocumentsDirectory()).path;

    UIUtils.showLoading();

    try {
      // 检查备份文件是否都存在
      List<File> backupFiles = [];
      bool allFilesExist = true;

      for (final item in _backupItems) {
        final zipFile = File(path.join(backupFolder, item.zipFileName));
        backupFiles.add(zipFile);

        if (!await zipFile.exists()) {
          logger.e("备份文件不存在: ${zipFile.path}");
          allFilesExist = false;
          break;
        }
      }

      if (!allFilesExist) {
        logger.e("备份文件不完整，无法恢复");
        Get.back();
        return false;
      }

      // 恢复所有备份项
      for (int i = 0; i < _backupItems.length; i++) {
        final item = _backupItems[i];
        final zipFile = backupFiles[i];

        // 确定恢复的目标路径
        String destinationPath;
        if (item.zipFileName == 'objectbox.zip') {
          destinationPath = path.join(appDocDir, ObjectboxService.dbDir);
        } else {
          // 为其他项目从源路径提取目标目录名
          final dirName = path.basename(item.sourcePath);
          destinationPath = path.join(appDocDir, dirName);
        }

        logger.i("恢复备份 ${item.name} 到 $destinationPath");
        await _extractZipFile(zipFile, destinationPath);
      }

      logger.i("恢复备份完成: $backupFolder => $appDocDir");
      Get.back();
      return true;
    } catch (e) {
      logger.e("恢复备份失败: $e");
      Get.back();
      return false;
    }
  }

  // 解压缩文件
  Future<void> _extractZipFile(File zipFile, String destinationPath) async {
    final bytes = await zipFile.readAsBytes();
    final zipArchive = archive.ZipDecoder().decodeBytes(bytes);

    for (final file in zipArchive) {
      final filename = file.name;
      if (file.isFile) {
        final data = file.content as List<int>;
        File(path.join(destinationPath, filename))
          ..createSync(recursive: true)
          ..writeAsBytesSync(data);
      } else {
        Directory(path.join(destinationPath, filename)).createSync(recursive: true);
      }
    }
  }
}
