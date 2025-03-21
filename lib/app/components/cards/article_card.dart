import 'dart:io';

import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/styles/component_style.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/components/articles/article_info_item.dart';
import 'package:daily_satori/app/components/articles/article_action_bar.dart';

/// 文章卡片组件
class ArticleCard extends StatelessWidget {
  final ArticleModel articleModel;
  final VoidCallback? onArticleUpdated;

  const ArticleCard({super.key, required this.articleModel, this.onArticleUpdated});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: InkWell(
        onTap: () => Get.toNamed(Routes.ARTICLE_DETAIL, arguments: articleModel),
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
    final hasImage = articleModel.hasHeaderImage();
    final imagePath = articleModel.getHeaderImagePath();
    final isProcessing = articleModel.entity.status != 'completed' && articleModel.entity.status != '';
    final hasTitle =
        (articleModel.aiTitle != null && articleModel.aiTitle!.isNotEmpty) ||
        (articleModel.title != null && articleModel.title!.isNotEmpty);

    return Stack(
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (hasImage) _buildImage(context, imagePath),
            if (hasImage) const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [if (hasTitle || !isProcessing) _buildTitle(context) else _buildUrlAsTitle(context)],
              ),
            ),
          ],
        ),
        if (isProcessing)
          Positioned(
            top: 0,
            right: 0,
            child: SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation<Color>(AppTheme.getColorScheme(context).primary),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildUrlAsTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Text(
      articleModel.url ?? '',
      style: textTheme.titleMedium?.copyWith(color: colorScheme.onSurfaceVariant),
      maxLines: 3,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildTitle(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return Text(
      articleModel.aiTitle ?? articleModel.title ?? '',
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
    final url = Uri.parse(articleModel.url ?? '');

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
            text: articleModel.createdAt != null ? GetTimeAgo.parse(articleModel.createdAt!, pattern: 'MM-dd') : '未知时间',
          ),
          const Spacer(),
          ArticleActionBar(articleModel: articleModel, onArticleUpdated: onArticleUpdated),
        ],
      ),
    );
  }
}
