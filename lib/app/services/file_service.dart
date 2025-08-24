import 'dart:io';
import 'dart:math';

import 'package:intl/intl.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';

class FileService {
  // 单例模式
  FileService._();
  static final FileService _instance = FileService._();
  static FileService get i => _instance;

  // 路径存储
  late String _appPath;
  late String _imagesBasePath;
  late String _diaryImagesBasePath;
  late String _screenshotsBasePath;
  late String _downloadsPath;
  late String _publicPath;
  late String _tempPath;

  // Getters
  String get imagesBasePath => _imagesBasePath;
  String get diaryImagesBasePath => _diaryImagesBasePath;
  String get screenshotsBasePath => _screenshotsBasePath;
  String get downloadsPath => _downloadsPath;
  String get publicPath => _publicPath;
  String get tempPath => _tempPath;
  String get dbPath => path.join(_appPath, ObjectboxService.dbDir);
  String get appPath => _appPath;

  // 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] FileService");
    _appPath = (await getApplicationDocumentsDirectory()).path;
    _imagesBasePath = await createDirectory('images');
    _diaryImagesBasePath = await createDirectory('diary_images');
    _screenshotsBasePath = await createDirectory('screenshots');
    _downloadsPath = await createDirectory('downloads');
    _publicPath = await createDirectory('public');
    _tempPath = (await getTemporaryDirectory()).path;

    // 确保公共静态资源目录存在
    await _initializePublicDirectory();
  }

  // 初始化公共静态资源目录结构
  Future<void> _initializePublicDirectory() async {
    // 创建子目录
    await createDirectory(path.join('public', 'css'));
    await createDirectory(path.join('public', 'js'));
    await createDirectory(path.join('public', 'img'));
    await createDirectory(path.join('public', 'fonts'));

    logger.i("公共静态资源目录结构已初始化");
  }

  // 获取下载文件路径
  String getDownloadPath(String fileName) => path.join(_downloadsPath, fileName);

  // 获取临时下载文件路径（更适合通过 FileProvider 对外共享/安装）
  Future<String> getTempDownloadPath(String fileName) async {
    final dir = Directory(path.join(_tempPath, 'downloads'));
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return path.join(dir.path, fileName);
  }

  // 获取图片路径
  String getImagePath(String imageName) => path.join(_imagesBasePath, imageName);

  // 获取公共资源文件路径
  String getPublicPath(String relativePath) => path.join(_publicPath, relativePath);

  // 生成截图路径
  String getScreenshotPath() {
    final timestamp = _getCurrentTimestamp();
    final randomValue = _generateRandomValue();
    return path.join(_screenshotsBasePath, "$timestamp-$randomValue.png");
  }

  // 根据URL生成文件名
  String generateFileNameByUrl(String url) {
    final extension = _getFileExtension(url);
    final timestamp = _getCurrentTimestamp();
    final randomValue = _generateRandomValue();
    return '$timestamp-$randomValue$extension';
  }

  // 创建目录
  Future<String> createDirectory(String name) async {
    final dirPath = path.join(appPath, name);
    final dir = Directory(dirPath);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dirPath;
  }

  // 保存文件到公共目录
  Future<String> saveToPublicDirectory(List<int> bytes, String relativePath) async {
    final fullPath = getPublicPath(relativePath);
    final file = File(fullPath);

    // 确保父目录存在
    final parentDir = Directory(path.dirname(fullPath));
    if (!parentDir.existsSync()) {
      parentDir.createSync(recursive: true);
    }

    await file.writeAsBytes(bytes);
    return relativePath; // 返回相对路径，便于构建URL
  }

  // 获取当前时间戳
  String _getCurrentTimestamp() => DateFormat('yyyyMMdd_HHmmss').format(DateTime.now());

  // 生成随机值
  String _generateRandomValue() => Random().nextInt(10000).toString();

  // 获取文件扩展名
  String _getFileExtension(String url) {
    const pattern = r'\b\w+\.(jpg|jpeg|png|gif|svg|webp)\b';
    final match = RegExp(pattern, caseSensitive: false).firstMatch(url.toLowerCase());
    return match != null ? '.${match.group(0)!.split('.').last}' : '';
  }

  // 将本地路径转换为Web路径
  String convertLocalPathToWebPath(String localPath) {
    return localPath.replaceAll(appPath, '');
  }

  // 解析并修复存储的本地媒体路径
  // 用于恢复后旧绝对路径无法直接访问的情况
  String resolveLocalMediaPath(String storedPath) {
    if (storedPath.isEmpty) return storedPath;

    final normalized = path.normalize(storedPath);

    // 若文件本身就存在，直接返回
    if (File(normalized).existsSync()) return normalized;

    // 分割为通用段，查找关键目录
    final segments = normalized.split(RegExp(r"[\\/]+"));
    final lowerSegments = segments.map((e) => e.toLowerCase()).toList();

    int idx = lowerSegments.indexWhere((s) => s == 'diary_images');
    if (idx != -1) {
      final rel = path.joinAll(segments.sublist(idx + 1));
      final candidate = path.join(_diaryImagesBasePath, rel);
      if (File(candidate).existsSync()) return candidate;

      // 尝试仅使用文件名（可能路径结构不同）
      final baseOnly = path.join(_diaryImagesBasePath, path.basename(rel));
      if (File(baseOnly).existsSync()) return baseOnly;
    }

    idx = lowerSegments.indexWhere((s) => s == 'images');
    if (idx != -1) {
      final rel = path.joinAll(segments.sublist(idx + 1));
      final candidate = path.join(_imagesBasePath, rel);
      if (File(candidate).existsSync()) return candidate;

      final baseOnly = path.join(_imagesBasePath, path.basename(rel));
      if (File(baseOnly).existsSync()) return baseOnly;
    }

    // 若原路径为相对路径，尝试拼接到两个根目录
    if (path.isRelative(normalized)) {
      final candidate1 = path.join(_diaryImagesBasePath, normalized);
      if (File(candidate1).existsSync()) return candidate1;

      final candidate2 = path.join(_imagesBasePath, normalized);
      if (File(candidate2).existsSync()) return candidate2;
    }

    // 最后兜底，返回原路径（由调用方决定回退到网络或占位）
    return storedPath;
  }
}
