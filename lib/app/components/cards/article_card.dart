import 'dart:io';

import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';

import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/component_style.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/components/articles/article_info_item.dart';
import 'package:daily_satori/app/components/articles/article_action_bar.dart';

/// 文章卡片组件
class ArticleCard extends StatelessWidget {
  final Article article;
  final VoidCallback? onArticleUpdated;

  const ArticleCard({super.key, required this.article, this.onArticleUpdated});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: InkWell(
        onTap: () => Get.toNamed(Routes.ARTICLE_DETAIL, arguments: article),
        borderRadius: BorderRadius.circular(10),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [_buildArticleContent(context), _buildActionBar(context)],
          ),
        ),
      ),
    );
  }

  Widget _buildArticleContent(BuildContext context) {
    final imagePath = article.images.isEmpty ? '' : article.images.first.path ?? '';
    final hasImage = imagePath.isNotEmpty;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (hasImage) _buildImage(context, imagePath),
        if (hasImage) const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [_buildTitle(context)])),
      ],
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Text(
      article.aiTitle ?? article.title ?? '',
      style: textTheme.titleMedium,
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildImage(BuildContext context, String path) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: SizedBox(
        width: 90,
        height: 70,
        child: Image.file(
          File(path),
          fit: BoxFit.cover,
          errorBuilder:
              (_, __, ___) => Container(
                width: 90,
                height: 70,
                decoration: ComponentStyle.imageContainerDecoration(context),
                child: Icon(Icons.image_not_supported, color: Theme.of(context).colorScheme.onSurfaceVariant, size: 24),
              ),
        ),
      ),
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final url = Uri.parse(article.url ?? '');

    return Container(
      margin: const EdgeInsets.only(top: 6),
      height: 24,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          ArticleInfoItem(icon: Icons.public, text: getTopLevelDomain(url.host)),
          const SizedBox(width: 12),
          ArticleInfoItem(
            icon: Icons.access_time,
            text: article.createdAt != null ? GetTimeAgo.parse(article.createdAt!, pattern: 'MM-dd') : '未知时间',
          ),
          const Spacer(),
          ArticleActionBar(article: article, onArticleUpdated: onArticleUpdated),
        ],
      ),
    );
  }
}
