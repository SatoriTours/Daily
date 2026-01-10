import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';

class HttpService implements AppService {
  HttpService._();
  static final HttpService _instance = HttpService._();
  static HttpService get i => _instance;

  @override
  String get serviceName => 'HttpService';
  @override
  ServicePriority get priority => ServicePriority.critical;

  late final Dio _dio;

  /// 通用 GET 请求
  Future<Response> get(String url, {Map<String, dynamic>? queryParameters, Options? options}) =>
      _dio.get(url, queryParameters: queryParameters, options: options);

  @override
  Future<void> init() async {
    _dio = Dio(BaseOptions(
      connectTimeout: NetworkConfig.timeout,
      receiveTimeout: NetworkConfig.timeout,
      sendTimeout: NetworkConfig.timeout,
      followRedirects: true,
      maxRedirects: 5,
    ));

    if (!AppInfoUtils.isProduction) {
      (_dio.httpClientAdapter as IOHttpClientAdapter).createHttpClient = () {
        final client = HttpClient();
        client.badCertificateCallback = (cert, host, port) => true;
        return client;
      };
    }
  }

  // 下载图片
  Future<String> downloadImage(String url) async {
    if (url.isEmpty) return '';
    final imageName = FileService.i.generateFileNameByUrl(url);
    final relativePath = FileService.i.getImagePath(imageName);
    final absolutePath = FileService.i.toAbsolutePath(relativePath);
    return await _downloadImageFile(url, absolutePath) ? relativePath : '';
  }

  // 下载文件
  Future<String> downloadFile(String url) async {
    if (url.isEmpty) return '';
    logger.i("开始下载文件: $url");
    final fileName = url.split('/').last;
    final isApk = fileName.toLowerCase().endsWith('.apk');
    final savePath = isApk
        ? await FileService.i.getTempDownloadPath(fileName)
        : FileService.i.getDownloadPath(fileName);
    return await _downloadAnyFile(url, savePath) ? savePath : '';
  }

  // 下载文件（带进度）
  Future<String> downloadFileWithProgress(
    String url, {
    void Function(int received, int total)? onProgress,
  }) async {
    if (url.isEmpty) return '';
    logger.i("开始下载文件(带进度): $url");
    final fileName = url.split('/').last;
    final isApk = fileName.toLowerCase().endsWith('.apk');
    final savePath = isApk
        ? await FileService.i.getTempDownloadPath(fileName)
        : FileService.i.getDownloadPath(fileName);
    return await _downloadAnyFileWithProgress(url, savePath, onProgress: onProgress) ? savePath : '';
  }

  // 获取文本内容
  Future<String> getTextContent(String url) async {
    try {
      final response = await _dio.get(url);
      return response.data.toString();
    } catch (e) {
      logger.i("获取文本内容失败: $url => $e");
      return '';
    }
  }

  // 内部方法

  Future<bool> _downloadImageFile(String url, String savePath) async {
    try {
      final response = await _dio.download(url, savePath, options: Options(
        receiveTimeout: DownloadConfig.imageReceiveTimeout,
        sendTimeout: DownloadConfig.imageSendTimeout,
        followRedirects: true,
      ));

      final contentType = (response.headers.value('content-type') ?? '').toLowerCase();
      if (!contentType.startsWith('image/') || contentType.contains('svg') || contentType.contains('avif')) {
        _safeDelete(savePath);
        logger.i("下载文件失败: 不受支持的内容类型 $contentType");
        return false;
      }

      final file = File(savePath);
      if (!await file.exists()) {
        logger.i("下载文件失败: 文件不存在");
        return false;
      }
      final length = await file.length();
      if (length < 16) {
        _safeDelete(savePath);
        logger.i("下载文件失败: 文件过小($length 字节)");
        return false;
      }

      final raf = await file.open();
      final bytes = await raf.read(16);
      await raf.close();

      return _looksLikeSupportedImage(bytes);
    } catch (e) {
      logger.i("下载文件失败: $e");
      return false;
    }
  }

  Future<bool> _downloadAnyFile(String url, String savePath) =>
      _downloadAnyFileWithProgress(url, savePath);

  Future<bool> _downloadAnyFileWithProgress(
    String url,
    String savePath, {
    void Function(int received, int total)? onProgress,
  }) async {
    try {
      await _dio.download(url, savePath, onReceiveProgress: onProgress, options: Options(
        responseType: ResponseType.bytes,
        followRedirects: true,
        validateStatus: (code) => code != null && code < 400,
        receiveTimeout: DownloadConfig.defaultReceiveTimeout,
        sendTimeout: DownloadConfig.defaultSendTimeout,
      ));

      final file = File(savePath);
      if (!await file.exists()) {
        logger.i("下载文件失败: 文件不存在");
        return false;
      }
      final length = await file.length();
      if (length < 16) {
        _safeDelete(savePath);
        logger.i("下载文件失败: 文件过小($length 字节)");
        return false;
      }

      try {
        final raf = await file.open();
        final head = await raf.read(64);
        await raf.close();
        final headStr = String.fromCharCodes(head).toLowerCase();
        if (headStr.contains('<html') || (headStr.contains('github') && headStr.contains('error'))) {
          _safeDelete(savePath);
          logger.i("下载文件失败: 看起来是错误页而非二进制文件");
          return false;
        }
      } catch (_) {}

      return true;
    } catch (e) {
      logger.i("下载文件失败: $e");
      return false;
    }
  }

  void _safeDelete(String path) {
    try {
      final f = File(path);
      if (f.existsSync()) f.deleteSync();
    } catch (_) {}
  }

  bool _looksLikeSupportedImage(List<int> bytes) {
    if (bytes.length < 12) return false;
    if (bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) {
      return true;
    }
    if (bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 &&
        bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
      return true;
    }
    if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38 &&
        (bytes[4] == 0x37 || bytes[4] == 0x39) && bytes[5] == 0x61) {
      return true;
    }
    if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 &&
        bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
      return true;
    }
    return false;
  }

  @override
  void dispose() {}
}
