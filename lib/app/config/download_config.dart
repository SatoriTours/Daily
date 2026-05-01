/// 下载配置
class DownloadConfig {
  DownloadConfig._();

  static const Duration defaultReceiveTimeout = Duration(minutes: 15); // 默认接收超时
  static const Duration defaultSendTimeout = Duration(minutes: 2); // 默认发送超时
  static const Duration imageReceiveTimeout = Duration(minutes: 5); // 图片接收超时
  static const Duration imageSendTimeout = Duration(minutes: 1); // 图片发送超时
}
