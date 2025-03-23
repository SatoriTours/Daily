import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 日记模块的工具类
class DiaryUtils {
  /// 在当前光标位置插入Markdown内容
  static void insertMarkdown(TextEditingController controller, String markdown) {
    final int currentPosition = controller.selection.baseOffset;

    // 处理光标位置无效的情况
    if (currentPosition < 0) {
      controller.text = controller.text + markdown;
      controller.selection = TextSelection.collapsed(offset: controller.text.length);
      return;
    }

    // 在光标位置插入Markdown
    final String newText =
        controller.text.substring(0, currentPosition) + markdown + controller.text.substring(currentPosition);

    controller.text = newText;
    controller.selection = TextSelection.collapsed(offset: currentPosition + markdown.length);
  }

  /// 显示Markdown预览
  static void showMarkdownPreview(BuildContext context, String content) {
    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: Text(
              '预览',
              style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontWeight: FontWeight.w600),
            ),
            content: SizedBox(
              width: double.maxFinite,
              height: 400,
              child: Markdown(
                data: content,
                styleSheet: getMarkdownStyleSheet(context),
                softLineBreak: true,
                selectable: true,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                imageBuilder: (uri, title, alt) {
                  // 处理Markdown中的图片
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8.0),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(8.0),
                      child: Image.network(
                        uri.toString(),
                        fit: BoxFit.contain,
                        errorBuilder:
                            (context, error, stackTrace) => Container(
                              height: 150,
                              decoration: BoxDecoration(
                                color: DiaryStyle.tagBackgroundColor(context),
                                borderRadius: BorderRadius.circular(8.0),
                              ),
                              child: Center(
                                child: Icon(Icons.broken_image_outlined, color: DiaryStyle.secondaryTextColor(context)),
                              ),
                            ),
                      ),
                    ),
                  );
                },
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text('关闭', style: TextStyle(color: DiaryStyle.accentColor(context))),
              ),
            ],
            backgroundColor: DiaryStyle.bottomSheetColor(context),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
    );
  }

  /// 获取Markdown样式表
  static MarkdownStyleSheet getMarkdownStyleSheet(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return MarkdownStyleSheet(
      // 段落样式优化
      p: TextStyle(color: DiaryStyle.primaryTextColor(context), fontSize: 15.0, height: 1.4, letterSpacing: 0.2),

      // 标题样式优化
      h1: TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        color: DiaryStyle.primaryTextColor(context),
        height: 1.3,
        letterSpacing: 0.2,
      ),
      h2: TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        color: DiaryStyle.primaryTextColor(context),
        height: 1.3,
        letterSpacing: 0.1,
      ),
      h3: TextStyle(
        fontSize: 17,
        fontWeight: FontWeight.w600,
        color: DiaryStyle.primaryTextColor(context),
        height: 1.3,
      ),
      h4: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: DiaryStyle.primaryTextColor(context),
        height: 1.3,
      ),

      // 引用样式优化
      blockquote: TextStyle(
        color: DiaryStyle.secondaryTextColor(context),
        fontStyle: FontStyle.italic,
        fontSize: 15.0,
        height: 1.4,
      ),
      blockquoteDecoration: BoxDecoration(
        border: Border(left: BorderSide(color: DiaryStyle.accentColor(context).withAlpha(128), width: 3.0)),
      ),
      blockquotePadding: const EdgeInsets.only(left: 12.0, top: 6.0, bottom: 6.0),

      // 代码样式优化
      code: TextStyle(
        color: isDarkMode ? Colors.greenAccent[200] : Colors.green[800],
        backgroundColor: isDarkMode ? Colors.grey[850] : DiaryStyle.inputBackgroundColor(context),
        fontFamily: 'monospace',
        fontSize: 14.0,
      ),
      codeblockDecoration: BoxDecoration(
        color: isDarkMode ? Colors.grey[850] : DiaryStyle.inputBackgroundColor(context),
        borderRadius: BorderRadius.circular(6.0),
      ),
      codeblockPadding: const EdgeInsets.all(8.0),

      // 列表样式优化
      listBullet: TextStyle(color: DiaryStyle.accentColor(context), fontSize: 14),
      listIndent: 20.0,

      // 链接样式优化
      a: TextStyle(
        color: DiaryStyle.accentColor(context),
        fontSize: 15.0,
        decoration: TextDecoration.underline,
        decorationColor: DiaryStyle.accentColor(context).withAlpha(102),
      ),

      // 强调样式优化
      em: TextStyle(fontStyle: FontStyle.italic, color: DiaryStyle.primaryTextColor(context), fontSize: 15.0),
      strong: TextStyle(fontWeight: FontWeight.w700, color: DiaryStyle.primaryTextColor(context), fontSize: 15.0),

      // 段间距优化
      blockSpacing: 12.0,
      horizontalRuleDecoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: DiaryStyle.secondaryTextColor(context).withAlpha(77), width: 1.0)),
      ),
      tableBorder: TableBorder.all(color: DiaryStyle.secondaryTextColor(context).withAlpha(77), width: 1.0),
      tableHeadAlign: TextAlign.center,
      tableCellsPadding: const EdgeInsets.all(6.0),
    );
  }

  /// 从内容中提取标签
  static String extractTags(String content) {
    final RegExp tagRegex = RegExp(r'#([a-zA-Z0-9\u4e00-\u9fa5]+)');
    final Iterable<RegExpMatch> matches = tagRegex.allMatches(content);

    final Set<String> tags = <String>{};
    for (final match in matches) {
      final tag = match.group(1);
      if (tag != null && tag.isNotEmpty) {
        tags.add(tag);
      }
    }

    return tags.join(',');
  }

  /// 格式化日期 - flomo风格
  static String formatDate(DateTime date) {
    final now = DateTime.now();
    final yesterday = DateTime(now.year, now.month, now.day - 1);
    final dateOnly = DateTime(date.year, date.month, date.day);

    if (dateOnly.isAtSameMomentAs(DateTime(now.year, now.month, now.day))) {
      return '今天';
    } else if (dateOnly.isAtSameMomentAs(yesterday)) {
      return '昨天';
    } else {
      return DateFormat('yyyy年MM月dd日').format(date);
    }
  }

  /// 打开URL链接
  static Future<void> launchURL(String url) async {
    try {
      final Uri uri = Uri.parse(url);
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        logger.e('无法打开链接: $url');
      }
    } catch (e) {
      logger.e('打开链接出错: $e');
    }
  }
}
