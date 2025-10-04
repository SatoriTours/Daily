import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

/// 文章信息项组件
class ArticleInfoItem extends GetView<ArticlesController> {
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
        Icon(icon, size: 14, color: colorScheme.onSurfaceVariant.withAlpha(179)),
        const SizedBox(width: 4),
        Text(
          text,
          style: textTheme.labelSmall?.copyWith(color: colorScheme.onSurfaceVariant.withAlpha(179)),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}
