import 'dart:io';

import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';
import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/global.dart';

import 'package:daily_satori/app/components/empty_states/articles_empty_view.dart';
import 'package:daily_satori/app/components/inputs/search_text_field.dart';
import 'package:daily_satori/app/components/lists/articles_list.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  PreferredSizeWidget _buildAppBar(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return AppBar(
      title: Obx(() => Text(controller.appBarTitle(), style: textTheme.titleLarge?.copyWith(color: Colors.white))),
      centerTitle: true,
      leading: IconButton(icon: const Icon(Icons.menu), onPressed: () => Get.toNamed(Routes.LEFT_BAR)),
      actions: [
        IconButton(icon: const Icon(Icons.search), onPressed: controller.toggleSearchState),
        Obx(() {
          if (WebService.i.webSocketTunnel.isConnected.value) {
            return IconButton(icon: const Icon(Icons.circle, color: Colors.green), onPressed: () {});
          } else {
            return IconButton(icon: const Icon(Icons.circle, color: Colors.red), onPressed: () {});
          }
        }),
      ],
    );
  }

  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.articles.isEmpty) {
        return ArticlesEmptyView(
          onAddArticle: () {
            // 这里可以添加引导用户添加文章的逻辑
          },
        );
      }
      return Column(
        children: [
          if (controller.enableSearch.value)
            SearchTextField(
              controller: controller.searchController,
              hintText: '搜索文章',
              isVisible: controller.enableSearch.value,
              onClear: controller.searchArticles,
              onSubmitted: (_) => controller.searchArticles(),
            ),
          Expanded(
            child: ArticlesList(
              articles: controller.articles,
              scrollController: controller.scrollController,
              onRefresh: controller.reloadArticles,
              isLoading: controller.isLoading.value,
              onArticleUpdated: () => controller.updateArticleInList(controller.articles.last.id),
            ),
          ),
        ],
      );
    });
  }
}

class ArticleCard extends GetView<ArticlesController> {
  final Article article;

  const ArticleCard({super.key, required this.article});

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
    final colorScheme = AppTheme.getColorScheme(context);

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
                color: colorScheme.surfaceVariant,
                child: Icon(Icons.image_not_supported, color: colorScheme.onSurfaceVariant),
              ),
        ),
      ),
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final url = Uri.parse(article.url ?? '');
    final colorScheme = AppTheme.getColorScheme(context);

    return Container(
      margin: const EdgeInsets.only(top: 6),
      height: 24,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        children: [
          _buildInfoItem(context, Icons.public, getTopLevelDomain(url.host)),
          const SizedBox(width: 12),
          _buildInfoItem(
            context,
            Icons.access_time,
            article.createdAt != null ? GetTimeAgo.parse(article.createdAt!, pattern: 'MM-dd') : '未知时间',
          ),
          const Spacer(),
          _buildActionButton(
            context,
            article.isFavorite ? Icons.favorite : Icons.favorite_border,
            article.isFavorite ? colorScheme.error : colorScheme.onSurfaceVariant.withOpacity(0.7),
            () async {
              await ArticleService.i.toggleFavorite(article.id);
              controller.updateArticleInList(article.id);
            },
          ),
          const SizedBox(width: 8),
          _buildActionButton(context, Icons.share, colorScheme.onSurfaceVariant.withOpacity(0.7), () {
            Share.share(article.url ?? '', subject: article.aiTitle ?? article.title ?? '');
          }),
        ],
      ),
    );
  }

  Widget _buildInfoItem(BuildContext context, IconData icon, String text) {
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

  Widget _buildActionButton(BuildContext context, IconData icon, Color color, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(width: 28, height: 24, alignment: Alignment.center, child: Icon(icon, size: 16, color: color)),
    );
  }
}
