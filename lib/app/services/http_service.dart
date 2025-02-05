import 'package:dio/dio.dart';
import 'package:sentry_dio/sentry_dio.dart';

import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

class HttpService {
  // 单例模式
  HttpService._();
  static final HttpService _instance = HttpService._();
  static HttpService get i => _instance;

  // 超时时间常量
  static const _timeoutDuration = Duration(seconds: 3);

  // Dio 实例
  late final Dio _dio;
  Dio get dio => _dio;

  Future<void> init() async {
    logger.i("[初始化服务] HttpService");
    _dio = Dio(BaseOptions(
      connectTimeout: _timeoutDuration,
      receiveTimeout: _timeoutDuration,
      sendTimeout: _timeoutDuration,
    ));
    dio.addSentry();
  }

  /// 下载图片
  /// [url] 图片URL
  /// 返回本地保存路径,失败返回空字符串
  Future<String> downloadImage(String url) async {
    if (url.isEmpty) return '';

    final imageName = FileService.i.generateFileNameByUrl(url);
    final imagePath = FileService.i.getImagePath(imageName);

    return await _downloadFile(url, imagePath) ? imagePath : '';
  }

  /// 下载文件
  /// [url] 文件URL
  /// 返回本地保存路径,失败返回空字符串
  Future<String> downloadFile(String url) async {
    if (url.isEmpty) return '';

    final fileName = url.split('/').last;
    final filePath = FileService.i.getDownloadPath(fileName);

    return await _downloadFile(url, filePath) ? filePath : '';
  }

  /// 内部下载方法
  /// [url] 下载URL
  /// [savePath] 保存路径
  /// 返回是否下载成功
  Future<bool> _downloadFile(String url, String savePath) async {
    try {
      final response = await dio.download(url, savePath);

      // 不支持SVG格式
      if (response.headers.value('content-type')?.contains('svg') == true) {
        logger.i("下载文件失败: 不支持SVG格式");
        return false;
      }

      return true;
    } catch (e) {
      logger.i("下载文件失败: $e");
      return false;
    }
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
}
