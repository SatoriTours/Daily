import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/dimensions.dart';

/// Markdown样式类
///
/// 提供优化的Markdown样式，使其看起来更像专业出版物
class MarkdownStyles {
  /// 获取Markdown样式表
  ///
  /// 根据当前主题和上下文返回优化的Markdown样式表
  static MarkdownStyleSheet getStyleSheet(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return MarkdownStyleSheet(
      // 文本样式
      p: textTheme.bodyMedium,

      // 标题样式
      h1: textTheme.headlineMedium?.copyWith(
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
        height: 1.3,
        color: colorScheme.onSurface,
      ),
      h2: textTheme.headlineSmall?.copyWith(
        fontWeight: FontWeight.w600,
        letterSpacing: -0.5,
        height: 1.3,
        color: colorScheme.onSurface,
      ),
      h3: textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600, height: 1.3, color: colorScheme.onSurface),
      h4: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600, height: 1.3, color: colorScheme.onSurface),
      h5: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.onSurface),
      h6: textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600, color: colorScheme.onSurface),

      // 强调样式
      em: TextStyle(fontStyle: FontStyle.italic, color: colorScheme.onSurface),
      strong: TextStyle(fontWeight: FontWeight.w700, color: colorScheme.onSurface),

      // 引用样式
      blockquote: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurface, fontStyle: FontStyle.italic),
      blockquoteDecoration: BoxDecoration(
        border: Border(left: BorderSide(color: colorScheme.primary.withAlpha(70), width: 4)),
        color: colorScheme.surfaceContainerHighest.withAlpha(30),
      ),
      blockquotePadding: const EdgeInsets.only(left: 16, top: 8, bottom: 8, right: 8),

      // 代码样式
      code: textTheme.bodySmall?.copyWith(
        fontFamily: 'monospace',
        backgroundColor: colorScheme.surfaceContainerHighest.withAlpha(50),
      ),
      codeblockDecoration: BoxDecoration(
        color: colorScheme.surfaceContainerHighest.withAlpha(70),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      codeblockPadding: const EdgeInsets.all(12),

      // 列表样式
      listBullet: textTheme.bodyMedium?.copyWith(color: colorScheme.primary, fontWeight: FontWeight.w600),
      listIndent: 24,

      // 表格样式
      tableHead: TextStyle(fontWeight: FontWeight.w700, color: colorScheme.onSurface),
      tableBorder: TableBorder.all(color: colorScheme.outlineVariant, width: 0.5),
      tableColumnWidth: const FlexColumnWidth(),
      tableCellsPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),

      // 水平线样式
      horizontalRuleDecoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: colorScheme.outlineVariant, width: 1)),
      ),

      // 链接样式
      a: TextStyle(
        color: colorScheme.primary,
        fontWeight: FontWeight.w500,
        decoration: TextDecoration.underline,
        decorationColor: colorScheme.primary.withAlpha(40),
      ),

      // 段落间距 - 优化列表项间距
      h1Padding: const EdgeInsets.only(top: 32, bottom: 20),
      h2Padding: const EdgeInsets.only(top: 28, bottom: 18),
      h3Padding: const EdgeInsets.only(top: 24, bottom: 16),
      h4Padding: const EdgeInsets.only(top: 20, bottom: 14),
      h5Padding: const EdgeInsets.only(top: 16, bottom: 12),
      h6Padding: const EdgeInsets.only(top: 12, bottom: 8),
      pPadding: const EdgeInsets.only(bottom: 16),
      // 减小列表项间距，使其更紧凑
      listBulletPadding: const EdgeInsets.only(bottom: 4),
    );
  }
}
