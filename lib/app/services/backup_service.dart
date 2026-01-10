import 'dart:io';
import 'package:archive/archive.dart' as arch;
import 'package:flutter/foundation.dart';
import 'package:flutter_archive/flutter_archive.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';

/// 备份服务
class BackupService extends AppService {
  BackupService._();
  static final BackupService i = BackupService._();

  @override
  final ServicePriority priority = ServicePriority.normal;

  final ValueNotifier<bool> isBackingUp = ValueNotifier(false);
  final ValueNotifier<double> backupProgress = ValueNotifier(0.0);

  String get backupDir => SettingRepository.i.getSetting(SettingService.backupDirKey);
  File get _timeFile => File(path.join(backupDir, 'backup_time.txt'));
  int get _interval => AppInfoUtils.isProduction ? BackupConfig.productionIntervalHours : BackupConfig.developmentIntervalHours;

  /// 备份项列表
  List<({String name, String source, String zip})> get items {
    final dbPath = FileService.i.dbPath;
    final imagesPath = FileService.i.toAbsolutePath(FileService.i.imagesBasePath);
    final diaryPath = FileService.i.toAbsolutePath(FileService.i.diaryImagesBasePath);
    logger.i('[备份] dbPath: $dbPath');
    logger.i('[备份] imagesPath: $imagesPath');
    logger.i('[备份] diaryPath: $diaryPath');
    return [
      (name: '数据库', source: dbPath, zip: 'objectbox.zip'),
      (name: '网页图片', source: imagesPath, zip: 'images.zip'),
      (name: '日记图片', source: diaryPath, zip: 'diary_images.zip'),
    ];
  }

  @override
  Future<void> init() async {
    await _fixImagePaths();
    if (AppInfoUtils.isProduction) checkAndBackup();
  }

  /// 检查并执行备份
  Future<bool> checkAndBackup() async {
    if (backupDir.isEmpty || isBackingUp.value) return false;

    isBackingUp.value = true;
    backupProgress.value = 0.0;

    try {
      final last = await _lastBackupTime();
      if (DateTime.now().difference(last).inHours >= _interval) {
        await _performBackup();
        return true;
      }
      return false;
    } finally {
      isBackingUp.value = false;
    }
  }

  /// 立即备份
  Future<bool> backupNow() async {
    logger.i('[备份] backupNow called');
    logger.i('[备份] backupDir: $backupDir, isBackingUp: ${isBackingUp.value}');
    if (backupDir.isEmpty || isBackingUp.value) {
      logger.i('[备份] 提前返回: backupDir=$backupDir, isBackingUp=${isBackingUp.value}');
      return false;
    }

    isBackingUp.value = true;
    backupProgress.value = 0.0;

    try {
      logger.i('[备份] 开始执行 _performBackup');
      await _performBackup();
      return true;
    } catch (e, stack) {
      logger.e('[备份] 失败', error: e, stackTrace: stack);
      return false;
    } finally {
      isBackingUp.value = false;
    }
  }

  /// 恢复备份
  Future<bool> restore(String name) async {
    final folder = path.join(backupDir, 'daily_satori_backup_$name');
    final docs = (await getApplicationDocumentsDirectory()).path;

    if (!await _validateFiles(folder)) return false;

    ObjectboxService.i.dispose();

    for (final item in items) {
      final dest = item.zip == 'objectbox.zip'
          ? path.join(docs, ObjectboxService.dbDir)
          : path.join(docs, path.basename(item.source));
      await _extract(path.join(folder, item.zip), dest);
    }
    return true;
  }

  // ========== 内部方法 ==========

  Future<DateTime> _lastBackupTime() async {
    if (await _timeFile.exists()) {
      return DateTime.parse(await _timeFile.readAsString());
    }
    return DateTime.fromMillisecondsSinceEpoch(0);
  }

  Future<void> _performBackup() async {
    logger.i('[备份] backupDir: $backupDir');
    final ts = DateTime.now().toIso8601String().replaceAll(RegExp('[:.]+'), '-');
    final folder = path.join(backupDir, 'daily_satori_backup_$ts');
    logger.i('[备份] 备份目录: $folder');
    await Directory(backupDir).create(recursive: true);
    await Directory(folder).create();

    backupProgress.value = 0.1;
    final step = 0.9 / items.length;

    for (int i = 0; i < items.length; i++) {
      logger.i('[备份] 开始压缩 ${items[i].name}: ${items[i].source}');
      await _compress(items[i].source, path.join(folder, items[i].zip));
      backupProgress.value = 0.1 + step * (i + 1);
    }

    backupProgress.value = 1.0;
    await _timeFile.writeAsString(DateTime.now().toIso8601String());
    logger.i('[备份] 完成');
  }

  Future<void> _compress(String source, String target) async {
    final dir = Directory(source);
    final exists = await dir.exists();
    logger.i('[备份] 源目录是否存在: $exists, path: $source');
    if (!exists) return;

    await ZipFile.createFromDirectory(
      sourceDir: dir,
      zipFile: File(target),
      recurseSubDirs: true,
    );
    logger.i('[备份] 压缩完成: $target');
  }

  Future<bool> _validateFiles(String folder) async {
    for (final item in items) {
      if (!await File(path.join(folder, item.zip)).exists()) return false;
    }
    return true;
  }

  Future<void> _extract(String zipPath, String dest) async {
    final bytes = await File(zipPath).readAsBytes();
    final zip = arch.ZipDecoder().decodeBytes(bytes);

    for (final f in zip) {
      final p = path.join(dest, f.name);
      if (f.isFile) {
        File(p)..createSync(recursive: true)..writeAsBytesSync(f.content as List<int>);
      } else {
        Directory(p).createSync(recursive: true);
      }
    }
  }

  Future<void> _fixImagePaths() async {
    await _fixDiaryPaths();
    await _fixArticlePaths();
  }

  Future<void> _fixDiaryPaths() async {
    final diaries = DiaryRepository.i.findAll();
    if (diaries.isEmpty) return;

    final base = FileService.i.diaryImagesBasePath;

    for (final d in diaries) {
      final imgs = d.images?.split(',').where((e) => e.trim().isNotEmpty).toList() ?? [];
      if (imgs.isEmpty) continue;

      final fixed = imgs.map((p) => _mapPath(p, base, 'diary_images')).toList();
      if (fixed.any((p) => p != p)) {
        d.entity.images = fixed.join(',');
        DiaryRepository.i.save(d);
      }
    }
  }

  Future<void> _fixArticlePaths() async {
    final articles = ArticleRepository.i.allModels();
    if (articles.isEmpty) return;

    final base = FileService.i.imagesBasePath;

    for (final a in articles) {
      bool changed = false;

      if (a.coverImage != null && a.coverImage!.trim().isNotEmpty) {
        final mapped = _mapPath(a.coverImage!, base, 'images');
        if (mapped != a.coverImage) {
          a.coverImage = mapped;
          changed = true;
        }
      }

      for (final img in a.images) {
        if (img.path != null && img.path!.trim().isNotEmpty) {
          final mapped = _mapPath(img.path!, base, 'images');
          if (mapped != img.path) {
            img.path = mapped;
            changed = true;
          }
        }
      }

      if (changed) ArticleRepository.i.updateModel(a);
    }
  }

  String _mapPath(String oldPath, String base, String marker) {
    final norm = path.normalize(oldPath);
    final segs = norm.split(RegExp(r'[\\/]+'));
    final idx = segs.indexWhere((s) => s.toLowerCase() == marker.toLowerCase());

    if (idx != -1) return path.join(base, path.joinAll(segs.sublist(idx + 1)));
    if (path.isRelative(norm)) return path.join(base, norm);
    return oldPath;
  }
}
