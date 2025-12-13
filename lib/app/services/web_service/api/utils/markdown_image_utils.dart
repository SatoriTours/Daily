import 'package:daily_satori/app/services/file_service.dart';

/// Markdown 图片路径工具
///
/// 将本地图片路径转换为 Web 可访问路径。
class MarkdownImageUtils {
  static final _imgRegex = RegExp(r'!\[([^\]]*)\]\(([^)]+)\)');

  static String convertContentImages(String content) {
    if (content.isEmpty) return content;

    return content.replaceAllMapped(_imgRegex, (match) {
      final alt = match.group(1) ?? '';
      final originalPath = match.group(2) ?? '';
      final converted = FileService.i.convertLocalPathToWebPath(originalPath);
      return '![$alt]($converted)';
    });
  }
}
