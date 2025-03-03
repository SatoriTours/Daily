import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/app_theme.dart';

/// 文章信息项组件
class ArticleInfoItem extends StatelessWidget {
  final IconData icon;
  final String text;

  const ArticleInfoItem({super.key, required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: colorScheme.onSurfaceVariant.withOpacity(0.7)),
        const SizedBox(width: 4),
        Text(
          text,
          style: textTheme.labelSmall?.copyWith(color: colorScheme.onSurfaceVariant.withOpacity(0.7)),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}
