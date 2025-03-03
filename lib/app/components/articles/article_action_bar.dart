import 'package:flutter/material.dart';

import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 文章操作栏组件
class ArticleActionBar extends StatelessWidget {
  final Article article;
  final VoidCallback? onArticleUpdated;

  const ArticleActionBar({super.key, required this.article, this.onArticleUpdated});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildActionButton(
          context,
          article.isFavorite ? Icons.favorite : Icons.favorite_border,
          article.isFavorite ? AppColors.error(context) : AppColors.textSecondary(context).withOpacity(0.7),
          () async {
            await ArticleService.i.toggleFavorite(article.id);
            if (onArticleUpdated != null) {
              onArticleUpdated!();
            }
          },
        ),
        const SizedBox(width: 8),
        _buildActionButton(context, Icons.share, AppColors.textSecondary(context).withOpacity(0.7), () {
          Share.share(article.url ?? '', subject: article.aiTitle ?? article.title ?? '');
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
