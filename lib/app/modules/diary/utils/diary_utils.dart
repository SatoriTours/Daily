import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

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
            title: Text('预览', style: TextStyle(color: DiaryStyle.primaryTextColor(context))),
            content: SizedBox(
              width: double.maxFinite,
              height: 400,
              child: Markdown(data: content, styleSheet: getMarkdownStyleSheet(context)),
            ),
            actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('关闭'))],
            backgroundColor: DiaryStyle.bottomSheetColor(context),
          ),
    );
  }

  /// 获取Markdown样式表
  static MarkdownStyleSheet getMarkdownStyleSheet(BuildContext context) {
    return MarkdownStyleSheet(
      p: TextStyle(color: DiaryStyle.primaryTextColor(context)),
      h1: TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: DiaryStyle.primaryTextColor(context)),
      h2: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: DiaryStyle.primaryTextColor(context)),
      h3: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: DiaryStyle.primaryTextColor(context)),
      blockquote: TextStyle(color: DiaryStyle.secondaryTextColor(context), fontStyle: FontStyle.italic),
      code: TextStyle(
        color: DiaryStyle.accentColor(context),
        backgroundColor: DiaryStyle.inputBackgroundColor(context),
      ),
    );
  }

  /// 从内容中提取标签
  static String extractTags(String content) {
    final RegExp tagRegex = RegExp(r'#(\S+)');
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
}
