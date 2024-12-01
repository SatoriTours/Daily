import 'package:dio/dio.dart';
import 'package:sentry_dio/sentry_dio.dart';

import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

class HttpService {
  HttpService._privateConstructor();
  static final HttpService _instance = HttpService._privateConstructor();
  static HttpService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] HttpService");
    _dio = Dio(BaseOptions(
      connectTimeout: const Duration(seconds: 3),
      receiveTimeout: const Duration(seconds: 3),
      sendTimeout: const Duration(seconds: 3),
    ));
    dio.addSentry();
  }

  late Dio _dio;

  Dio get dio => _dio;

  Future<String> downloadImage(String url) async {
    if (url.isEmpty) {
      return "";
    }

    final imageName = FileService.i.generateFileNameByUrl(url);
    final imagePath = FileService.i.getImagePath(imageName);

    if (await _downloadFile(url, imagePath)) {
      return imagePath;
    }

    return '';
  }

  Future<String> downloadFile(String url) async {
    if (url.isEmpty) {
      return "";
    }

    String fileName = url.split('/').last;
    String filePath = FileService.i.getDownloadPath(fileName);

    if (await _downloadFile(url, filePath)) {
      return filePath;
    }

    return '';
  }

  Future<bool> _downloadFile(String url, String savePath) async {
    try {
      final response = await dio.download(
        url,
        savePath,
      );
      if (response.headers.value('content-type')?.contains('svg') == true) {
        logger.i("下载文件失败, 文件类型为svg");
        return false;
      }
      return true;
    } catch (e) {
      logger.i("下载文件失败, $e");
    }
    return false;
  }
}
