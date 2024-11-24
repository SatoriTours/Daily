import 'dart:io';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';
import 'package:path/path.dart';

class FreeDiskService {
  FreeDiskService._privateConstructor();
  static final FreeDiskService _instance = FreeDiskService._privateConstructor();
  static FreeDiskService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] FreeDiskService");
  }

  Future<void> clean() async {
    // 查询 ArticleImages 和 ArticleScreenshoots 里的图片
    final imageBox = ObjectboxService.i.box<Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    final images = imageBox.getAll();
    final screenshots = screenshotBox.getAll();

    List<String?> imageFiles = images.map((i) => i.path).toList();
    List<String?> screenshootsFiles = screenshots.map((i) => i.path).toList();

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
