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
  late String _downloadsPath;
  late String _publicPath;
  late String _tempPath;

  // Getters
  String get imagesBasePath => _imagesBasePath;
  String get diaryImagesBasePath => _diaryImagesBasePath;
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
    _downloadsPath = await createDirectory('downloads');
    _publicPath = await createDirectory('public');
    _tempPath = (await getTemporaryDirectory()).path;

    // 确保公共静态资源目录存在
    await _initializePublicDirectory();

    // 日志输出各路径
    logger.i("应用根目录: $_appPath");
    logger.i("图片目录: $_imagesBasePath");
    logger.i("日记图片目录: $_diaryImagesBasePath");
    logger.i("下载目录: $_downloadsPath");
    logger.i("公共资源目录: $_publicPath");
    logger.i("临时目录: $_tempPath");
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

  // ========================================================================
  // 路径生成方法 - 返回相对路径（用于存储到数据库）
  // ========================================================================

  /// 获取图片的相对路径（用于存储到数据库）
  String getImagePath(String imageName) => path.join(_imagesBasePath, imageName);

  /// 获取日记图片的相对路径（用于存储到数据库）
  String getDiaryImagePath(String imageName) => path.join(_diaryImagesBasePath, imageName);

  /// 获取公共资源的相对路径
  String getPublicPath(String relativePath) => path.join(_publicPath, relativePath);

  /// 根据URL生成文件名（仅文件名，不包含路径）
  String generateFileNameByUrl(String url) {
    final extension = _getFileExtension(url);
    final timestamp = _getCurrentTimestamp();
    final randomValue = _generateRandomValue();
    return '$timestamp-$randomValue$extension';
  }

  // ========================================================================
  // 路径转换方法 - 相对路径与绝对路径互转
  // ========================================================================

  /// 将相对路径转换为绝对路径（用于实际文件操作）
  /// [relativePath] 相对于 appPath 的路径，如 images/xxx.jpg
  /// 返回完整的绝对路径
  String toAbsolutePath(String relativePath) {
    if (relativePath.isEmpty) return relativePath;

    // 如果已经是绝对路径，直接返回
    if (path.isAbsolute(relativePath)) return relativePath;

    return path.join(_appPath, relativePath);
  }

  /// 将绝对路径转换为相对路径（用于存储到数据库）
  /// [absolutePath] 绝对路径
  /// 返回相对于 appPath 的路径
  String toRelativePath(String absolutePath) {
    if (absolutePath.isEmpty) return absolutePath;

    // 如果已经是相对路径，直接返回
    if (path.isRelative(absolutePath)) return absolutePath;

    // 如果路径以 appPath 开头，截取相对部分
    if (absolutePath.startsWith(_appPath)) {
      var relative = absolutePath.substring(_appPath.length);
      // 移除开头的路径分隔符
      if (relative.startsWith('/') || relative.startsWith('\\')) {
        relative = relative.substring(1);
      }
      return relative;
    }

    // 尝试从已知目录名称提取相对路径
    final knownDirs = ['images', 'diary_images', 'public', 'downloads'];
    for (final dir in knownDirs) {
      final idx = absolutePath.indexOf('/$dir/');
      if (idx != -1) {
        return absolutePath.substring(idx + 1); // 返回 dir/xxx 格式
      }
    }

    // 无法转换，返回原路径
    return absolutePath;
  }

  // 创建目录
  Future<String> createDirectory(String name) async {
    final dirPath = path.join(appPath, name);
    final dir = Directory(dirPath);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return toRelativePath(dirPath);
  }

  // 保存文件到公共目录
  Future<String> saveToPublicDirectory(List<int> bytes, String relativePath) async {
    // getPublicPath 返回相对路径如 public/css/style.css
    final relPath = getPublicPath(relativePath);
    // 转换为绝对路径用于实际文件操作
    final fullPath = toAbsolutePath(relPath);
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
    if (localPath.isEmpty) return localPath;

    // 查找 /images/ 或 /diary_images/ 的位置，直接截取
    final imagesIndex = localPath.indexOf('/images/');
    if (imagesIndex != -1) {
      return localPath.substring(imagesIndex);
    }

    final diaryImagesIndex = localPath.indexOf('/diary_images/');
    if (diaryImagesIndex != -1) {
      return localPath.substring(diaryImagesIndex);
    }

    return localPath;
  }

  // ========================================================================
  // 路径解析方法 - 处理存储的路径，返回可用的绝对路径
  // ========================================================================

  /// 解析并修复存储的本地媒体路径
  /// 支持相对路径（新格式）和绝对路径（旧格式，用于兼容历史数据）
  /// 返回可用的绝对路径
  String resolveLocalMediaPath(String storedPath) {
    if (storedPath.isEmpty) return storedPath;

    final normalized = path.normalize(storedPath);

    // 1. 如果是相对路径（新格式），直接拼接 appPath
    if (path.isRelative(normalized)) {
      final absolutePath = path.join(_appPath, normalized);
      if (File(absolutePath).existsSync()) return absolutePath;

      // 尝试添加扩展名
      final withExt = _tryFileWithExtensions(absolutePath);
      if (withExt != null) return withExt;
    }

    // 2. 若文件本身就存在（绝对路径且有效），直接返回
    if (File(normalized).existsSync()) return normalized;

    // 3. 尝试添加常见扩展名
    final triedPath = _tryFileWithExtensions(normalized);
    if (triedPath != null) return triedPath;

    // 4. 兼容旧绝对路径：提取关键目录后的相对部分，重新拼接
    final segments = normalized.split(RegExp(r"[\\/]+"));
    final lowerSegments = segments.map((e) => e.toLowerCase()).toList();

    int idx = lowerSegments.indexWhere((s) => s == 'diary_images');
    if (idx != -1) {
      final rel = path.joinAll(segments.sublist(idx + 1));
      final candidate = path.join(_diaryImagesBasePath, rel);

      if (File(candidate).existsSync()) return candidate;

      final withExt = _tryFileWithExtensions(candidate);
      if (withExt != null) return withExt;

      final baseOnly = path.join(_diaryImagesBasePath, path.basename(rel));
      if (File(baseOnly).existsSync()) return baseOnly;

      final baseWithExt = _tryFileWithExtensions(baseOnly);
      if (baseWithExt != null) return baseWithExt;
    }

    idx = lowerSegments.indexWhere((s) => s == 'images');
    if (idx != -1) {
      final rel = path.joinAll(segments.sublist(idx + 1));
      final candidate = path.join(_imagesBasePath, rel);

      if (File(candidate).existsSync()) return candidate;

      final withExt = _tryFileWithExtensions(candidate);
      if (withExt != null) return withExt;

      final baseOnly = path.join(_imagesBasePath, path.basename(rel));
      if (File(baseOnly).existsSync()) return baseOnly;

      final baseWithExt = _tryFileWithExtensions(baseOnly);
      if (baseWithExt != null) return baseWithExt;
    }

    // 5. 最后兜底，返回原路径（由调用方决定回退到网络或占位）
    return storedPath;
  }

  // 尝试为文件路径添加常见扩展名
  String? _tryFileWithExtensions(String filePath) {
    // 如果已经有扩展名，不尝试
    if (path.extension(filePath).isNotEmpty) return null;

    // 常见图片扩展名列表
    const extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'];

    for (final ext in extensions) {
      final withExt = '$filePath$ext';
      if (File(withExt).existsSync()) {
        return withExt;
      }
    }

    return null;
  }

  /// 删除单个文件
  /// 返回是否成功删除
  Future<bool> deleteFile(String filePath) async {
    try {
      if (filePath.isEmpty) return false;

      final file = File(filePath);
      if (await file.exists()) {
        await file.delete();
        logger.d('[FileService] 已删除文件: $filePath');
        return true;
      } else {
        logger.w('[FileService] 文件不存在，无法删除: $filePath');
        return false;
      }
    } catch (e) {
      logger.e('[FileService] 删除文件失败: $filePath, 错误: $e');
      return false;
    }
  }

  /// 批量删除文件
  /// 返回成功删除的文件数量
  Future<int> deleteFiles(List<String> filePaths) async {
    int deletedCount = 0;
    for (final filePath in filePaths) {
      if (await deleteFile(filePath)) {
        deletedCount++;
      }
    }
    return deletedCount;
  }
}
