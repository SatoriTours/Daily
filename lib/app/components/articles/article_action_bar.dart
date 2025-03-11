import 'package:flutter/material.dart';

import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/global.dart';

/// 文章操作栏组件
class ArticleActionBar extends StatelessWidget {
  final ArticleModel articleModel;
  final VoidCallback? onArticleUpdated;

  const ArticleActionBar({super.key, required this.articleModel, this.onArticleUpdated});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildActionButton(
          context,
          articleModel.isFavorite ? Icons.favorite : Icons.favorite_border,
          articleModel.isFavorite ? colorScheme.error : colorScheme.onSurfaceVariant.withAlpha(179),
          () async {
            await articleModel.toggleFavorite();
            if (onArticleUpdated != null) {
              onArticleUpdated!();
            }
          },
        ),
        const SizedBox(width: 8),
        _buildActionButton(context, Icons.share, colorScheme.onSurfaceVariant.withAlpha(179), () {
          Share.share(articleModel.url ?? '', subject: articleModel.aiTitle ?? articleModel.title ?? '');
        }),
      ],
    );
  }

  Widget _buildActionButton(BuildContext context, IconData icon, Color color, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(width: 28, height: 24, alignment: Alignment.center, child: Icon(icon, size: 16, color: color)),
    );
  }
}
