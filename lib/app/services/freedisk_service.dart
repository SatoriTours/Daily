import 'dart:io';

import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/objectbox.g.dart';
import 'package:path/path.dart';

import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';

class FreeDiskService {
  // 单例模式
  FreeDiskService._();
  static final FreeDiskService _instance = FreeDiskService._();
  static FreeDiskService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] FreeDiskService");
  }

  /// 清理并备份图片文件
  Future<void> clean() async {
    await _backupAndCleanFiles<Image>(
      imagePaths: ObjectboxService.i.box<Article>().getAll().map((article) => article.coverImage).toList(),
      sourcePath: FileService.i.imagesBasePath,
      backupDirName: 'images_bak',
    );
  }

  /// 备份并清理指定类型的文件
  Future<void> _backupAndCleanFiles<T>({
    required List<String?> imagePaths,
    required String sourcePath,
    required String backupDirName,
  }) async {
    final files = imagePaths.where((e) => e != null).toList();
    final backupPath = await FileService.i.createDirectory(backupDirName);

    // 备份存在的文件
    for (var filePath in files) {
      try {
        final file = File(filePath!);
        if (await file.exists()) {
          final newFilePath = join(backupPath, basename(file.path));
          await file.copy(newFilePath);
        }
      } catch (e) {
        logger.d("备份文件失败: $e");
      }
    }

    // 清理并恢复
    await _removeDir(sourcePath);
    await Directory(backupPath).rename(sourcePath);
  }

  /// 删除指定目录
  Future<void> _removeDir(String path) async {
    final dir = Directory(path);
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }
  }
}
