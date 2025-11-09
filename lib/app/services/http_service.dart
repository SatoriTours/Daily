import 'dart:io';
import 'package:dio/dio.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';

class HttpService implements AppService {
  // 单例模式
  HttpService._();
  static final HttpService _instance = HttpService._();
  static HttpService get i => _instance;

  @override
  String get serviceName => 'HttpService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  // 基础网络超时时间（非大文件下载场景）
  static const _timeoutDuration = NetworkConfig.timeout;

  // Dio 实例
  late final Dio _dio;
  Dio get dio => _dio;

  @override
  Future<void> init() async {
    logger.i("[初始化服务] HttpService");
    _dio = Dio(
      BaseOptions(
        connectTimeout: _timeoutDuration,
        receiveTimeout: _timeoutDuration,
        sendTimeout: _timeoutDuration,
        followRedirects: true,
        maxRedirects: 5,
      ),
    );

    // 如需全局 HTTP 代理（例如企业网络或调试抓包），可在运行环境中设置：
    // - 环境变量 HTTP_PROXY/HTTPS_PROXY（系统级）
    // - 或基于 Dio 的自定义 adapter/proxy（此处不默认启用）
  }

  /// 下载图片
  /// [url] 图片URL
  /// 返回本地保存路径,失败返回空字符串
  Future<String> downloadImage(String url) async {
    if (url.isEmpty) {
      return '';
    }

    final imageName = FileService.i.generateFileNameByUrl(url);
    final imagePath = FileService.i.getImagePath(imageName);

    return await _downloadImageFile(url, imagePath) ? imagePath : '';
  }

  /// 下载文件
  /// [url] 文件URL
  /// 返回本地保存路径,失败返回空字符串
  Future<String> downloadFile(String url) async {
    if (url.isEmpty) {
      return '';
    }

    logger.i("开始下载文件: $url");

    final fileName = url.split('/').last;
    // APK 优先下载到临时目录，避免外部存储权限限制 & 提高安装器可访问性
    final isApk = fileName.toLowerCase().endsWith('.apk');
    final savePath = isApk
        ? await FileService.i.getTempDownloadPath(fileName)
        : FileService.i.getDownloadPath(fileName);

    // 直接使用原始地址下载
    if (await _downloadAnyFile(url, savePath)) return savePath;

    return '';
  }

  /// 内部下载图片方法（带图片类型与魔数校验）
  Future<bool> _downloadImageFile(String url, String savePath) async {
    try {
      final response = await dio.download(
        url,
        savePath,
        options: Options(
          // 大文件图片下载使用更长超时（注意：Options 不支持 connectTimeout 覆写）
          receiveTimeout: DownloadConfig.imageReceiveTimeout,
          sendTimeout: DownloadConfig.imageSendTimeout,
          followRedirects: true,
        ),
      );

      final contentType = (response.headers.value('content-type') ?? '').toLowerCase();

      // 仅接受图片类型，排除 svg/avif（避免本地解码失败）
      final isImage = contentType.startsWith('image/');
      final isSvg = contentType.contains('svg');
      final isAvif = contentType.contains('avif');
      if (!isImage || isSvg || isAvif) {
        _safeDelete(savePath);
        logger.i("下载文件失败: 不受支持的内容类型 $contentType");
        return false;
      }

      // 基础文件校验：存在且非空
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

      // 魔数校验：JPEG/PNG/GIF/WebP 之一
      final raf = await file.open();
      final bytes = await raf.read(16);
      await raf.close();

      if (!_looksLikeSupportedImage(bytes)) {
        _safeDelete(savePath);
        logger.i("下载文件失败: 文件签名非受支持图片");
        return false;
      }

      return true;
    } catch (e) {
      logger.i("下载文件失败: $e");
      return false;
    }
  }

  /// 通用文件下载（不限制为图片类型），做基础校验：存在且非空
  Future<bool> _downloadAnyFile(String url, String savePath) async {
    try {
      await dio.download(
        url,
        savePath,
        options: Options(
          responseType: ResponseType.bytes,
          followRedirects: true,
          validateStatus: (code) => code != null && code < 400,
          // APK 等大文件下载使用更长超时（注意：Options 不支持 connectTimeout 覆写）
          receiveTimeout: DownloadConfig.defaultReceiveTimeout,
          sendTimeout: DownloadConfig.defaultSendTimeout,
        ),
      );

      // 基础文件校验：存在且非空
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

      // 简单检查是否下载到了 HTML 错误页
      try {
        final raf = await file.open();
        final head = await raf.read(64);
        await raf.close();
        final headStr = String.fromCharCodes(head).toLowerCase();
        if (headStr.contains('<html') || headStr.contains('github') && headStr.contains('error')) {
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

  // 删除无效文件
  void _safeDelete(String path) {
    try {
      final f = File(path);
      if (f.existsSync()) {
        f.deleteSync();
      }
    } catch (_) {}
  }

  // 简单魔数校验常见图片格式
  bool _looksLikeSupportedImage(List<int> bytes) {
    if (bytes.length < 12) {
      return false;
    }
    // JPEG: FF D8 FF
    if (bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) {
      return true;
    }
    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47 &&
        bytes[4] == 0x0D &&
        bytes[5] == 0x0A &&
        bytes[6] == 0x1A &&
        bytes[7] == 0x0A) {
      return true;
    }
    // GIF: 'GIF87a' or 'GIF89a'
    if (bytes[0] == 0x47 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x38 &&
        (bytes[4] == 0x37 || bytes[4] == 0x39) &&
        bytes[5] == 0x61) {
      return true;
    }
    // WebP: RIFF....WEBP
    if (bytes[0] == 0x52 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x46 &&
        bytes[8] == 0x57 &&
        bytes[9] == 0x45 &&
        bytes[10] == 0x42 &&
        bytes[11] == 0x50) {
      return true;
    }
    return false;
  }

  /// 获取文本内容
  /// [url] 请求URL
  /// 返回文本内容,失败返回空字符串
  Future<String> getTextContent(String url) async {
    try {
      final response = await dio.get(url);
      return response.data.toString();
    } catch (e) {
      logger.i("获取文本内容失败: $url => $e");
      return '';
    }
  }

  @override
  void dispose() {}
}
