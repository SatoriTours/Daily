import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 文章信息项组件
///
/// 这是一个纯展示组件，用于显示图标和文本信息
/// 可以在多个地方复用（文章卡片、详情页等）
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
        Icon(icon, size: Dimensions.iconSizeXs, color: colorScheme.onSurfaceVariant.withAlpha(179)),
        Dimensions.horizontalSpacerXs,
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
