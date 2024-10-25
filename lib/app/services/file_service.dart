import 'dart:io';
import 'dart:math';

import 'package:daily_satori/global.dart';
import 'package:intl/intl.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

class FileService {
  FileService._privateConstructor();
  static final FileService _instance = FileService._privateConstructor();
  static FileService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] FileService");
    _appPath = (await getApplicationDocumentsDirectory()).path;
    _imagesBasePath = await mkdir('images');
    _screenshotsBasePath = await mkdir('screenshots');
  }

  late String _imagesBasePath;
  late String _screenshotsBasePath;
  late String _appPath;

  String get imagesBasePath => _imagesBasePath;
  String get screenshotsBasePath => _screenshotsBasePath;
  String get appPath => _appPath;

  String getImagePath(String imageName) {
    return path.join(_imagesBasePath, imageName);
  }

  String getScreenshotPath() {
    String timestamp = DateFormat('yyyyMMdd_HHmmss').format(DateTime.now());
    String randomValue = Random().nextInt(10000).toString();
    return path.join(_screenshotsBasePath, "$timestamp-$randomValue.png");
  }

  String generateFileNameByUrl(String url) {
    // 获取文件后缀名
    String extension = '';
    final RegExp pattern = RegExp(r'\b\w+\.(jpg|jpeg|png|gif|svg|webp)\b', caseSensitive: false);
    final match = pattern.firstMatch(url.toLowerCase());
    if (match != null) {
      extension = '.${match.group(0)!.split('.').last}'; // 返回格式名
    }

    // 获取当前时间
    String timestamp = DateFormat('yyyyMMdd_HHmmss').format(DateTime.now());

    // 生成随机数
    String randomValue = Random().nextInt(10000).toString();

    // 组合文件名
    return '$timestamp-$randomValue$extension';
  }

  Future<String> mkdir(String name) async {
    final dirPath = path.join(appPath, name);

    // 创建 目录（如果不存在）
    final dir = Directory(dirPath);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dirPath;
  }
}
