import 'dart:io';
import 'dart:math';

import 'package:intl/intl.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/service_base.dart';

class FileService extends AppService {
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

  String get imagesBasePath => _imagesBasePath;
  String get diaryImagesBasePath => _diaryImagesBasePath;
  String get downloadsPath => _downloadsPath;
  String get publicPath => _publicPath;
  String get tempPath => _tempPath;
  String get dbPath => path.join(_appPath, ObjectboxService.dbDir);
  String get appPath => _appPath;

  @override
  Future<void> init() async {
    _appPath = (await getApplicationDocumentsDirectory()).path;
    _imagesBasePath = await createDirectory('images');
    _diaryImagesBasePath = await createDirectory('diary_images');
    _downloadsPath = await createDirectory('downloads');
    _publicPath = await createDirectory('public');
    _tempPath = (await getTemporaryDirectory()).path;

    for (final sub in ['css', 'js', 'img', 'fonts']) {
      await createDirectory(path.join('public', sub));
    }
  }

  String getDownloadPath(String fileName) => path.join(_downloadsPath, fileName);

  Future<String> getTempDownloadPath(String fileName) async {
    final dir = Directory(path.join(_tempPath, 'downloads'));
    if (!await dir.exists()) await dir.create(recursive: true);
    return path.join(dir.path, fileName);
  }

  // 路径生成方法

  String getImagePath(String imageName) => path.join(_imagesBasePath, imageName);
  String getDiaryImagePath(String imageName) => path.join(_diaryImagesBasePath, imageName);
  String getPublicPath(String relativePath) => path.join(_publicPath, relativePath);

  String generateFileNameByUrl(String url) {
    final ext = _getFileExtension(url);
    final timestamp = DateFormat('yyyyMMdd_HHmmss').format(DateTime.now());
    return '$timestamp-${Random().nextInt(10000)}$ext';
  }

  // 路径转换方法

  String toAbsolutePath(String relativePath) {
    if (relativePath.isEmpty) return relativePath;
    if (path.isAbsolute(relativePath)) return relativePath;
    return path.join(_appPath, relativePath);
  }

  String toRelativePath(String absolutePath) {
    if (absolutePath.isEmpty) return absolutePath;
    if (path.isRelative(absolutePath)) return absolutePath;

    if (absolutePath.startsWith(_appPath)) {
      final relative = absolutePath.substring(_appPath.length);
      return relative.startsWith(RegExp(r'[/\\]')) ? relative.substring(1) : relative;
    }

    for (final dir in ['images', 'diary_images', 'public', 'downloads']) {
      final idx = absolutePath.indexOf('/$dir/');
      if (idx != -1) return absolutePath.substring(idx + 1);
    }

    return absolutePath;
  }

  // 文件操作方法

  Future<String> createDirectory(String name) async {
    final dirPath = path.join(appPath, name);
    final dir = Directory(dirPath);
    if (!await dir.exists()) await dir.create(recursive: true);
    return toRelativePath(dirPath);
  }

  Future<String> saveToPublicDirectory(List<int> bytes, String relativePath) async {
    final fullPath = toAbsolutePath(getPublicPath(relativePath));
    final parentDir = Directory(path.dirname(fullPath));
    if (!parentDir.existsSync()) parentDir.createSync(recursive: true);
    await File(fullPath).writeAsBytes(bytes);
    return relativePath;
  }

  String convertLocalPathToWebPath(String localPath) {
    if (localPath.isEmpty) return localPath;
    for (final dir in ['/images/', '/diary_images/']) {
      final idx = localPath.indexOf(dir);
      if (idx != -1) return localPath.substring(idx);
    }
    return localPath;
  }

  String resolveLocalMediaPath(String storedPath) {
    if (storedPath.isEmpty) return storedPath;

    final normalized = path.normalize(storedPath);
    final candidates = <String>[];

    if (path.isRelative(normalized)) {
      candidates.add(path.join(_appPath, normalized));
    } else {
      candidates.add(normalized);
    }

    for (final dir in ['images', 'diary_images']) {
      final segments = normalized.split(RegExp(r'[\\/]+'));
      final idx = segments.indexWhere((s) => s.toLowerCase() == dir);
      if (idx != -1) {
        final rel = path.joinAll(segments.sublist(idx + 1));
        candidates.add(path.join(_appPath, dir, rel));
        candidates.add(path.join(_appPath, dir, path.basename(rel)));
      }
    }

    for (final candidate in candidates) {
      if (File(candidate).existsSync()) return candidate;
      final withExt = _tryFileWithExtensions(candidate);
      if (withExt != null) return withExt;
    }

    return storedPath;
  }

  String? _tryFileWithExtensions(String filePath) {
    if (path.extension(filePath).isNotEmpty) return null;
    const extensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'];
    return extensions.firstWhere(
      (ext) => File('$filePath$ext').existsSync(),
      orElse: () => '',
    ).isNotEmpty
        ? '$filePath${extensions.firstWhere((ext) => File('$filePath$ext').existsSync())}'
        : null;
  }

  Future<bool> deleteFile(String filePath) async {
    if (filePath.isEmpty) return false;
    final file = File(filePath);
    if (!await file.exists()) return false;
    try {
      await file.delete();
      logger.d('[FileService] 已删除文件: $filePath');
      return true;
    } catch (e) {
      logger.e('[FileService] 删除文件失败: $filePath, 错误: $e');
      return false;
    }
  }

  Future<int> deleteFiles(List<String> filePaths) async {
    return Future.wait(filePaths.map(deleteFile)).then((results) => results.where((r) => r).length);
  }

  String _getFileExtension(String url) {
    const pattern = r'\b\w+\.(jpg|jpeg|png|gif|svg|webp)\b';
    final match = RegExp(pattern, caseSensitive: false).firstMatch(url.toLowerCase());
    return match != null ? '.${match.group(0)!.split('.').last}' : '';
  }
}
