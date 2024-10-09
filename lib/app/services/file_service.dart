import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import 'dart:io';
import 'package:intl/intl.dart';
import 'dart:math';

class FileService {
  FileService._privateConstructor();
  static final FileService _instance = FileService._privateConstructor();
  static FileService get instance => _instance;

  Future<void> init() async {
    final directory = await getApplicationDocumentsDirectory();
    final imagesPath = path.join(directory.path, 'images');

    // 创建 images 目录（如果不存在）
    final imagesDir = Directory(imagesPath);
    if (!await imagesDir.exists()) {
      await imagesDir.create(recursive: true);
    }

    _imagesPath = imagesPath;
  }

  late String _imagesPath;

  String get imagesPath => _imagesPath;

  String getImagePath(String imageName) {
    return path.join(_imagesPath, imageName);
  }

  String generateFileName(String url) {
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
}
