/// 图片配置
class ImageConfig {
  ImageConfig._();

  static const int maxUploadSize = 5 * 1024 * 1024; // 最大上传大小 5MB
  static const int maxWidth = 1920; // 最大宽度
  static const int maxHeight = 1080; // 最大高度
  static const Duration cacheDuration = Duration(days: 7); // 缓存时长
  static const Duration downloadTimeout = Duration(seconds: 30); // 下载超时时间
}
