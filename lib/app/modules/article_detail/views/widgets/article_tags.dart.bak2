import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/index.dart';

/// 文章标签组件
///
/// 显示文章的标签列表，支持：
/// - 美观的标签样式
/// - 自动换行
/// - 响应式布局
class ArticleTags extends StatelessWidget {
  final String tags;

  const ArticleTags({super.key, required this.tags});

  @override
  Widget build(BuildContext context) {
    if (tags.isEmpty) return const SizedBox.shrink();

    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);
    final tagList = tags.split(', ').where((tag) => tag.trim().isNotEmpty).toList();

    if (tagList.isEmpty) return const SizedBox.shrink();

    return Wrap(
      spacing: 6.0,
      runSpacing: 6.0,
      children: tagList.map((tag) => _buildTag(context, tag, colorScheme, textTheme)).toList(),
    );
  }

  /// 构建单个标签
  Widget _buildTag(BuildContext context, String tag, ColorScheme colorScheme, TextTheme textTheme) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [colorScheme.primaryContainer.withAlpha(80), colorScheme.primaryContainer.withAlpha(50)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: colorScheme.primary.withAlpha(40), width: 0.5),
        boxShadow: [BoxShadow(color: colorScheme.shadow.withAlpha(8), blurRadius: 2, offset: const Offset(0, 1))],
      ),
      padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 3.0),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 标签图标（更小）
          Icon(Icons.label_outline, size: 11, color: colorScheme.primary.withAlpha(180)),
          const SizedBox(width: 4),
          // 标签文本（更小）
          Text(
            tag,
            style: textTheme.labelSmall?.copyWith(
              color: colorScheme.onSurface.withAlpha(200),
              fontWeight: FontWeight.w500,
              letterSpacing: 0.1,
              fontSize: 11,
            ),
          ),
        ],
      ),
    );
  }
}
