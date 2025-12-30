import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'dart:io';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/config/app_config.dart';
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
import 'package:daily_satori/app/data/index.dart';

/// 备份项配置类，定义备份内容和目标
class BackupItem {
  /// 备份项名称
  final String name;

  /// 源路径
  final String sourcePath;

  /// 目标文件名
  final String zipFileName;

  const BackupItem({required this.name, required this.sourcePath, required this.zipFileName});
}

class BackupService {
  // 单例模式
  BackupService._();
  static final BackupService _instance = BackupService._();
  static BackupService get i => _instance;

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
  String get backupDir => SettingRepository.i.getSetting(SettingService.backupDirKey);
  File get backupTimeFile => File(path.join(backupDir, 'backup_time.txt'));
  int get _backupInterval =>
      AppInfoUtils.isProduction ? BackupConfig.productionIntervalHours : BackupConfig.developmentIntervalHours;
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

  /// 从备份恢复文件
  Future<bool> restoreBackup(String backupName) async {
    final backupFolder = path.join(backupDir, 'daily_satori_backup_$backupName');
    final appDocDir = (await getApplicationDocumentsDirectory()).path;

    DialogUtils.showLoading();

    try {
      // 检查备份文件是否完整
      final backupFiles = await _validateBackupFiles(backupFolder);
      if (backupFiles == null) {
        return false;
      }

      // 恢复所有备份项
      await _restoreAllBackupItems(backupFiles, appDocDir);

      // 修正图片路径
      await _fixAllImagePaths();

      logger.i("恢复备份完成: $backupFolder => $appDocDir");
      AppNavigation.back();
      return true;
    } catch (e) {
      logger.e("恢复备份失败: $e");
      AppNavigation.back();
      return false;
    }
  }

  /// 验证备份文件是否完整
  Future<List<File>?> _validateBackupFiles(String backupFolder) async {
    final backupFiles = <File>[];

    for (final item in _backupItems) {
      final zipFile = File(path.join(backupFolder, item.zipFileName));
      backupFiles.add(zipFile);

      if (!await zipFile.exists()) {
        logger.e("备份文件不存在: ${zipFile.path}");
        logger.e("备份文件不完整，无法恢复");
        AppNavigation.back();
        return null;
      }
    }

    return backupFiles;
  }

  /// 恢复所有备份项
  Future<void> _restoreAllBackupItems(List<File> backupFiles, String appDocDir) async {
    for (int i = 0; i < _backupItems.length; i++) {
      final item = _backupItems[i];
      final zipFile = backupFiles[i];

      final destinationPath = _getDestinationPath(item, appDocDir);

      logger.i("恢复备份 ${item.name} 到 $destinationPath");
      await _extractZipFile(zipFile, destinationPath);
    }
  }

  /// 获取备份项的目标路径
  String _getDestinationPath(BackupItem item, String appDocDir) {
    if (item.zipFileName == 'objectbox.zip') {
      return path.join(appDocDir, ObjectboxService.dbDir);
    } else {
      // 为其他项目从源路径提取目标目录名
      final dirName = path.basename(item.sourcePath);
      return path.join(appDocDir, dirName);
    }
  }

  /// 修正所有图片路径
  Future<void> _fixAllImagePaths() async {
    await _fixDiaryImagePaths();
    await _fixArticleImagePaths();
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

  /// 修复日记图片的绝对路径，将旧路径中的“diary_images/...”相对部分映射到当前目录
  Future<void> _fixDiaryImagePaths() async {
    try {
      final List<DiaryModel> diaries = DiaryRepository.i.findAll();
      if (diaries.isEmpty) return;

      final newBase = FileService.i.diaryImagesBasePath; // 当前设备的图片根目录

      for (final diary in diaries) {
        final images = diary.images;
        if (images == null || images.trim().isEmpty) continue;

        final parts = images.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
        bool changed = false;
        final fixed = <String>[];

        for (final p0 in parts) {
          final normalized = path.normalize(p0);
          // 兼容不同分隔符，定位到 diary_images 段
          final segments = normalized.split(RegExp(r"[\\/]+"));
          final idx = segments.indexWhere((s) => s.toLowerCase() == 'diary_images');
          if (idx != -1) {
            final relSegments = segments.sublist(idx + 1);
            final relPath = path.joinAll(relSegments);
            final mapped = path.join(newBase, relPath);
            if (mapped != p0) changed = true;
            fixed.add(mapped);
          } else {
            // 若未定位到标记目录且原路径是相对路径，则按相对路径拼接
            if (path.isRelative(normalized)) {
              final mapped = path.join(newBase, normalized);
              if (mapped != p0) changed = true;
              fixed.add(mapped);
            } else {
              // 保持原样
              fixed.add(p0);
            }
          }
        }

        if (changed) {
          diary.entity.images = fixed.join(',');
          DiaryRepository.i.save(diary);
        }
      }
      logger.i('恢复后已修复日记图片路径');
    } catch (e) {
      logger.w('修复日记图片路径时出错: $e');
    }
  }

  /// 修复文章本地图片路径（封面与图片表），将旧路径映射到当前 images 目录
  Future<void> _fixArticleImagePaths() async {
    try {
      final articles = ArticleRepository.i.allModels();
      if (articles.isEmpty) return;

      final newBase = FileService.i.imagesBasePath;

      for (final am in articles) {
        bool changed = false;

        // 修复封面路径
        final cover = am.coverImage;
        if (cover != null && cover.trim().isNotEmpty) {
          final mapped = _mapToNewBase(cover, newBase, marker: 'images');
          if (mapped != cover) {
            am.coverImage = mapped;
            changed = true;
          }
        }

        // 修复关联图片表路径
        try {
          final images = am.images; // List<obx_image.Image>
          for (final img in images) {
            final path0 = img.path;
            if (path0 != null && path0.trim().isNotEmpty) {
              final mapped = _mapToNewBase(path0, newBase, marker: 'images');
              if (mapped != path0) {
                img.path = mapped;
                changed = true;
              }
            }
          }
        } catch (_) {}

        if (changed) {
          ArticleRepository.i.updateModel(am);
        }
      }
      logger.i('恢复后已修复文章相关图片路径');
    } catch (e) {
      logger.w('修复文章图片路径时出错: $e');
    }
  }

  /// 将任意旧路径映射到新根目录（根据标记目录名提取相对路径）
  String _mapToNewBase(String oldPath, String newBase, {required String marker}) {
    final normalized = path.normalize(oldPath);
    final segments = normalized.split(RegExp(r"[\\/]+"));
    final idx = segments.indexWhere((s) => s.toLowerCase() == marker.toLowerCase());
    if (idx != -1) {
      final rel = path.joinAll(segments.sublist(idx + 1));
      return path.join(newBase, rel);
    }
    if (path.isRelative(normalized)) {
      return path.join(newBase, normalized);
    }
    return oldPath;
  }
}
