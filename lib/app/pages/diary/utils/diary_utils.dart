import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';
import 'package:daily_satori/app/pages/books/providers/books_controller_provider.dart';
import 'package:daily_satori/app/pages/home/providers/home_controller_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 日记模块的工具类
class DiaryUtils {
  /// 在当前光标位置插入Markdown内容
  static void insertMarkdown(TextEditingController controller, String markdown) {
    final int currentPosition = controller.selection.baseOffset;
    final int currentEndPosition = controller.selection.extentOffset;
    final bool hasSelection = currentPosition != currentEndPosition;

    // 处理光标位置无效的情况
    if (currentPosition < 0) {
      controller.text = controller.text + markdown;
      controller.selection = TextSelection.collapsed(offset: controller.text.length);
      return;
    }

    // 如果有选中文本，则对选中的文本应用格式
    if (hasSelection) {
      final String selectedText = controller.text.substring(
        currentPosition < currentEndPosition ? currentPosition : currentEndPosition,
        currentPosition < currentEndPosition ? currentEndPosition : currentPosition,
      );

      // 应用不同的格式
      String formattedText;
      if (markdown == '**文本**') {
        formattedText = '**$selectedText**';
      } else if (markdown == '*文本*') {
        formattedText = '*$selectedText*';
      } else if (markdown == '`代码`') {
        formattedText = '`$selectedText`';
      } else if (markdown == '[链接文本](https://example.com)') {
        // 检查选中的文本是否是URL
        if (isUrl(selectedText)) {
          formattedText = '[链接文本]($selectedText)';
        } else {
          formattedText = '[$selectedText](https://example.com)';
        }
      } else if (markdown == '# ') {
        formattedText = '# $selectedText';
      } else if (markdown == '> 引用文本') {
        formattedText = '> $selectedText';
      } else {
        // 如果没有特殊处理的格式，则直接插入原始markdown
        formattedText = markdown;
      }

      // 替换选中的文本
      final newText = controller.text.replaceRange(
        currentPosition < currentEndPosition ? currentPosition : currentEndPosition,
        currentPosition < currentEndPosition ? currentEndPosition : currentPosition,
        formattedText,
      );

      controller.text = newText;
      // 更新光标位置
      final newCursorPosition =
          (currentPosition < currentEndPosition ? currentPosition : currentEndPosition) + formattedText.length;
      controller.selection = TextSelection.collapsed(offset: newCursorPosition);
    } else {
      // 在光标位置插入Markdown
      final String newText =
          controller.text.substring(0, currentPosition) + markdown + controller.text.substring(currentPosition);

      controller.text = newText;
      controller.selection = TextSelection.collapsed(offset: currentPosition + markdown.length);
    }
  }

  /// 检查文本是否为URL
  static bool isUrl(String text) {
    final urlPattern = RegExp(r'^(http|https)://[a-zA-Z0-9-_.]+\.[a-zA-Z]{2,}(:[0-9]+)?(/.*)?$', caseSensitive: false);
    return urlPattern.hasMatch(text.trim());
  }

  /// 自动将粘贴的链接转换为Markdown链接格式
  static String autoConvertLinks(String text) {
    // 匹配URL模式
    final RegExp urlRegex = RegExp(
      r'(https?:\/\/(?:www\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|www\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|https?:\/\/(?:www\.|(?!www))[a-zA-Z0-9]+\.[^\s]{2,}|www\.[a-zA-Z0-9]+\.[^\s]{2,})',
      caseSensitive: false,
    );

    // 如果文本只包含一个URL且没有其他内容，则转换为Markdown链接
    if (urlRegex.hasMatch(text) && urlRegex.stringMatch(text) == text) {
      return '[链接]($text)';
    }

    return text;
  }

  /// 显示Markdown预览
  static void showMarkdownPreview(BuildContext context, String content) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          '预览',
          style: TextStyle(color: DiaryStyles.getPrimaryTextColor(context), fontWeight: FontWeight.w600),
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
            // ignore: deprecated_member_use
            imageBuilder: (Uri uri, String? title, String? alt) {
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 8.0),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8.0),
                  child: Image.network(
                    uri.toString(),
                    fit: BoxFit.contain,
                    errorBuilder: (context, error, stackTrace) => Container(
                      height: 150,
                      decoration: BoxDecoration(
                        color: DiaryStyles.getTagBackgroundColor(context),
                        borderRadius: BorderRadius.circular(8.0),
                      ),
                      child: Center(
                        child: Icon(Icons.broken_image_outlined, color: DiaryStyles.getSecondaryTextColor(context)),
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
            onPressed: () => AppNavigation.back(),
            child: Text('关闭', style: TextStyle(color: DiaryStyles.getAccentColor(context))),
          ),
        ],
        backgroundColor: DiaryStyles.getBottomSheetColor(context),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
    );
  }

  /// 获取Markdown样式表
  static MarkdownStyleSheet getMarkdownStyleSheet(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return MarkdownStyleSheet(
      // 段落样式优化
      p: TextStyle(color: DiaryStyles.getPrimaryTextColor(context), fontSize: 15.0, height: 1.4, letterSpacing: 0.2),

      // 标题样式优化
      h1: TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        color: DiaryStyles.getPrimaryTextColor(context),
        height: 1.3,
        letterSpacing: 0.2,
      ),
      h2: TextStyle(
        fontSize: 18,
        fontWeight: FontWeight.w600,
        color: DiaryStyles.getPrimaryTextColor(context),
        height: 1.3,
        letterSpacing: 0.1,
      ),
      h3: TextStyle(
        fontSize: 17,
        fontWeight: FontWeight.w600,
        color: DiaryStyles.getPrimaryTextColor(context),
        height: 1.3,
      ),
      h4: TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: DiaryStyles.getPrimaryTextColor(context),
        height: 1.3,
      ),

      // 引用样式优化
      blockquote: TextStyle(
        color: DiaryStyles.getSecondaryTextColor(context),
        fontStyle: FontStyle.italic,
        fontSize: 15.0,
        height: 1.4,
      ),
      blockquoteDecoration: BoxDecoration(
        border: Border(left: BorderSide(color: DiaryStyles.getAccentColor(context).withAlpha(128), width: 3.0)),
      ),
      blockquotePadding: const EdgeInsets.only(left: 12.0, top: 6.0, bottom: 6.0),

      // 代码样式优化
      code: TextStyle(
        color: isDarkMode ? Colors.greenAccent[200] : Colors.green[800],
        backgroundColor: isDarkMode ? Colors.grey[850] : DiaryStyles.getInputBackgroundColor(context),
        fontFamily: 'monospace',
        fontSize: 14.0,
      ),
      codeblockDecoration: BoxDecoration(
        color: isDarkMode ? Colors.grey[850] : DiaryStyles.getInputBackgroundColor(context),
        borderRadius: BorderRadius.circular(6.0),
      ),
      codeblockPadding: const EdgeInsets.all(8.0),

      // 列表样式优化
      listBullet: TextStyle(color: DiaryStyles.getAccentColor(context), fontSize: 14),
      listIndent: 20.0,

      // 链接样式优化
      a: TextStyle(
        color: DiaryStyles.getAccentColor(context),
        fontSize: 15.0,
        decoration: TextDecoration.underline,
        decorationColor: DiaryStyles.getAccentColor(context).withAlpha(102),
      ),

      // 强调样式优化
      em: TextStyle(fontStyle: FontStyle.italic, color: DiaryStyles.getPrimaryTextColor(context), fontSize: 15.0),
      strong: TextStyle(fontWeight: FontWeight.w700, color: DiaryStyles.getPrimaryTextColor(context), fontSize: 15.0),

      // 段间距优化
      blockSpacing: 12.0,
      horizontalRuleDecoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: DiaryStyles.getSecondaryTextColor(context).withAlpha(77), width: 1.0)),
      ),
      tableBorder: TableBorder.all(color: DiaryStyles.getSecondaryTextColor(context).withAlpha(77), width: 1.0),
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

  /// 打开读书页并定位到指定观点
  static Future<void> openBookViewpoint(WidgetRef ref, int viewpointId) async {
    logger.d('打开读书观点: ID=$viewpointId');
    try {
      // 切换到底部导航的“读书”页（索引：文章0、日记1、读书2、我的3）
      ref.read(homeControllerProvider.notifier).changePage(2);

      // 交给读书控制器处理查找与跳转
      ref.read(booksControllerProvider.notifier).openViewpointById(viewpointId);
    } catch (e) {
      logger.e('打开读书观点失败: $e');
    }
  }
}
