import 'package:daily_satori/global.dart';
import 'package:dio/dio.dart';

import 'package:daily_satori/app/services/file_service.dart';

class HttpService {
  HttpService._privateConstructor();
  static final HttpService _instance = HttpService._privateConstructor();
  static HttpService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] HttpService");
    _dio = Dio();
  }

  late Dio _dio;

  Dio get dio => _dio;

  Future<String> downloadImage(String url) async {
    if (url.isEmpty) {
      return "";
    }

    final imageName = FileService.i.generateFileNameByUrl(url);
    final imagePath = FileService.i.getImagePath(imageName);

    await dio.download(
      url,
      imagePath,
    );

    return imagePath;
  }
}
