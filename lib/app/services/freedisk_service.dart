import 'dart:io';
import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/global.dart';
import 'package:path/path.dart';

class FreeDiskService {
  FreeDiskService._privateConstructor();
  static final FreeDiskService _instance = FreeDiskService._privateConstructor();
  static FreeDiskService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] FreeDiskService");
  }

  AppDatabase get db => DBService.i.db;

  Future<void> clean() async {
    // 查询 ArticleImages 和 ArticleScreenshoots 里的图片
    final images = await (db.select(db.articleImages)).get();
    final screenshots = await (db.select(db.articleScreenshoots)).get();
    final articles = await (db.select(db.articles)).get();

    List<String?> imageFiles = images.map((i) => i.imagePath).toList() + articles.map((i) => i.imagePath).toList();
    List<String?> screenshootsFiles =
        screenshots.map((i) => i.imagePath).toList() + articles.map((i) => i.screenshotPath).toList();

    final imagesBakPath = await FileService.i.mkdir('images_bak');
    final screenshotsBakPath = await FileService.i.mkdir('screenshots_bak');

    // 把存在的文件拷贝到备份目录
    for (var filePath in imageFiles) {
      if (filePath == null) continue;
      try {
        final file = File(filePath);
        if (await file.exists()) {
          final newFilePath = join(imagesBakPath, basename(file.path));
          await file.copy(newFilePath);
        }
      } catch (e) {
        logger.d("拷贝文件时出错: $e");
      }
    }

    for (var filePath in screenshootsFiles) {
      if (filePath == null) continue;
      try {
        final file = File(filePath);
        if (await file.exists()) {
          final newFilePath = join(screenshotsBakPath, basename(file.path));
          await file.copy(newFilePath);
        }
      } catch (e) {
        logger.d("拷贝文件时出错: $e");
      }
    }

    await _removeDir(FileService.i.imagesBasePath);
    await _removeDir(FileService.i.screenshotsBasePath);

    await Directory(imagesBakPath).rename(FileService.i.imagesBasePath);
    await Directory(screenshotsBakPath).rename(FileService.i.screenshotsBasePath);
  }

  Future<void> _removeDir(String path) async {
    final dir = Directory(path);
    if (await dir.exists()) await dir.delete(recursive: true);
  }
}
