import 'package:daily_satori/app/services/logger_service.dart';

/// 字符串工具类
class StringUtils {
  // 私有构造函数，防止实例化
  StringUtils._();

  /// 检查文本是否包含中文字符
  static bool isChinese(String text) => RegExp(r'[\u4e00-\u9fa5]').hasMatch(text);

  /// 获取文本的子串,可指定长度和后缀
  static String getSubstring(String text, {int length = 50, String suffix = ''}) {
    if (length < 0) throw ArgumentError('length不能为负数');
    return text.length > length ? '${text.substring(0, length)}$suffix' : text;
  }

  /// 获取文本的第一行
  static String firstLine(String text) => text.split('\n').first;

  /// 从主机名获取顶级域名
  static String getTopLevelDomain(String? host) {
    if (host == null) return '';

    final parts = host.split('.');
    if (parts.length < 2) return host;

    return '${parts[parts.length - 2]}.${parts.last}';
  }

  /// 从文本中提取URL
  static String getUrlFromText(String text) {
    final urlPattern = RegExp(
      r'https?:\/\/[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&\/=]*)',
      caseSensitive: false,
    );

    final url = urlPattern.firstMatch(text)?.group(0) ?? '';
    if (url.startsWith('http://')) {
      final httpsUrl = url.replaceFirst('http://', 'https://');
      logger.i("[checkClipboardText] 将 http 链接替换为 https: $httpsUrl");
      return httpsUrl;
    }
    return url;
  }
}
