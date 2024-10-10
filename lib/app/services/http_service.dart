import 'package:daily_satori/app/services/file_service.dart';
import 'package:dio/dio.dart';

class HttpService {
  HttpService._privateConstructor();
  static final HttpService _instance = HttpService._privateConstructor();
  static HttpService get instance => _instance;

  Future<void> init() async {
    _dio = Dio();
  }

  late Dio _dio;

  Dio get dio => _dio;

  Future<String> downloadImage(String url) async {
    if (url.isEmpty) {
      return "";
    }

    final imageName = FileService.instance.generateFileNameByUrl(url);
    final imagePath = FileService.instance.getImagePath(imageName);

    await dio.download(
      url,
      imagePath,
    );

    return imagePath;
  }
}
