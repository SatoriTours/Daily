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
      p: textTheme.bodyMedium?.copyWith(height: 1.8, letterSpacing: 0.2),

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
      em: TextStyle(fontStyle: FontStyle.italic, color: colorScheme.onSurfaceVariant),
      strong: TextStyle(fontWeight: FontWeight.w700, color: colorScheme.onSurface),

      // 引用样式
      blockquote: textTheme.bodyMedium?.copyWith(
        color: colorScheme.onSurfaceVariant,
        fontStyle: FontStyle.italic,
        height: 1.6,
      ),
      blockquoteDecoration: BoxDecoration(
        border: Border(left: BorderSide(color: colorScheme.primary.withAlpha(70), width: 4)),
        color: colorScheme.surfaceVariant.withAlpha(30),
      ),
      blockquotePadding: const EdgeInsets.only(left: 16, top: 8, bottom: 8, right: 8),

      // 代码样式
      code: textTheme.bodySmall?.copyWith(
        fontFamily: 'monospace',
        backgroundColor: colorScheme.surfaceVariant.withAlpha(50),
        letterSpacing: -0.2,
        height: 1.5,
        fontSize: (textTheme.bodySmall?.fontSize ?? 12) * 0.95,
      ),
      codeblockDecoration: BoxDecoration(
        color: colorScheme.surfaceVariant.withAlpha(70),
        borderRadius: BorderRadius.circular(Dimensions.radiusS),
      ),
      codeblockPadding: const EdgeInsets.all(12),

      // 列表样式
      listBullet: textTheme.bodyMedium?.copyWith(color: colorScheme.primary, fontWeight: FontWeight.bold),
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

      // 段落间距
      h1Padding: const EdgeInsets.only(top: 32, bottom: 16),
      h2Padding: const EdgeInsets.only(top: 28, bottom: 14),
      h3Padding: const EdgeInsets.only(top: 24, bottom: 12),
      h4Padding: const EdgeInsets.only(top: 20, bottom: 10),
      h5Padding: const EdgeInsets.only(top: 16, bottom: 8),
      h6Padding: const EdgeInsets.only(top: 12, bottom: 6),
      pPadding: const EdgeInsets.only(bottom: 16),
      listBulletPadding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
    );
  }
}
