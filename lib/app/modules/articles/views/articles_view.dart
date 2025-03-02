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
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/global.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Obx(() => Text(controller.appBarTitle(), style: MyFontStyle.appBarTitleStyleThemed(context))),
      centerTitle: true,
      leading: IconButton(icon: const Icon(Icons.menu), onPressed: () => Get.toNamed(Routes.LEFT_BAR)),
      actions: [
        IconButton(icon: const Icon(Icons.search), onPressed: controller.toggleSearchState),
        Obx(() {
          if (WebService.i.webSocketTunnel.isConnected.value) {
            return IconButton(icon: const Icon(Icons.circle, color: Colors.green), onPressed: () {});
          } else {
            return const SizedBox.shrink();
          }
        }),
      ],
    );
  }

  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.articles.isEmpty) {
        return _buildEmptyView(context);
      }
      return Column(
        children: [
          if (controller.enableSearch.value) _buildSearchTextField(context),
          Expanded(child: _buildArticlesList(context)),
        ],
      );
    });
  }

  Widget _buildEmptyView(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.article_outlined, size: 64, color: AppColors.textSecondary(context).withOpacity(0.5)),
          const SizedBox(height: 16),
          Text('还没有收藏内容', style: MyFontStyle.emptyStateStyleThemed(context)),
          const SizedBox(height: 8),
          Text('您可以通过分享功能添加新文章', style: MyFontStyle.cardSubtitleStyleThemed(context)),
        ],
      ),
    );
  }

  Widget _buildSearchTextField(BuildContext context) {
    return AnimatedSlide(
      offset: Offset(0, controller.enableSearch.value ? 0 : -1),
      duration: const Duration(milliseconds: 300),
      child: Container(
        margin: const EdgeInsets.all(16),
        child: TextField(
          controller: controller.searchController,
          decoration: InputDecoration(
            hintText: '搜索文章',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () {
                controller.searchController.clear();
                controller.searchArticles();
              },
            ),
          ),
          onSubmitted: (_) => controller.searchArticles(),
        ),
      ),
    );
  }

  Widget _buildArticlesList(BuildContext context) {
    return RefreshIndicator(
      onRefresh: controller.reloadArticles,
      color: AppColors.primary(context),
      child: ListView.builder(
        controller: controller.scrollController,
        itemCount: _calculateItemCount(),
        itemBuilder: (context, index) => _buildListItem(context, index),
        padding: const EdgeInsets.only(bottom: 16),
      ),
    );
  }

  int _calculateItemCount() {
    return controller.articles.length + (controller.isLoading.value ? 1 : 0);
  }

  Widget _buildListItem(BuildContext context, int index) {
    if (index == controller.articles.length) {
      return const Center(child: Padding(padding: EdgeInsets.all(16.0), child: CircularProgressIndicator()));
    }
    return ArticleCard(article: controller.articles[index]);
  }
}

class ArticleCard extends GetView<ArticlesController> {
  final Article article;

  const ArticleCard({super.key, required this.article});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: () => Get.toNamed(Routes.ARTICLE_DETAIL, arguments: article),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildArticleContent(context),
              const SizedBox(height: 12),
              Divider(color: AppColors.divider(context)),
              const SizedBox(height: 4),
              _buildActionBar(context),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildArticleContent(BuildContext context) {
    final imagePath = article.images.isEmpty ? '' : article.images.first.path ?? '';

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [_buildTitle(context)])),
        if (imagePath.isNotEmpty) ...[const SizedBox(width: 16), _buildImage(context, imagePath)],
      ],
    );
  }

  Widget _buildTitle(BuildContext context) {
    return Text(
      article.aiTitle ?? article.title ?? '',
      style: MyFontStyle.listTitleStyleThemed(context),
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
  }

  Widget _buildImage(BuildContext context, String path) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: SizedBox(
        width: 80,
        height: 80,
        child: Image.file(
          File(path),
          fit: BoxFit.cover,
          errorBuilder:
              (_, __, ___) => Container(
                width: 80,
                height: 80,
                color: AppColors.divider(context),
                child: Icon(Icons.image_not_supported, color: AppColors.textSecondary(context)),
              ),
        ),
      ),
    );
  }

  Widget _buildActionBar(BuildContext context) {
    final url = Uri.parse(article.url ?? '');

    return Row(
      children: [
        Icon(Icons.public, size: 16, color: AppColors.textSecondary(context)),
        const SizedBox(width: 4),
        Text(getTopLevelDomain(url.host), style: MyFontStyle.cardSubtitleStyleThemed(context)),
        const SizedBox(width: 16),
        if (article.createdAt != null) ...[
          Icon(Icons.access_time, size: 16, color: AppColors.textSecondary(context)),
          const SizedBox(width: 4),
          Text(
            GetTimeAgo.parse(article.createdAt!, pattern: 'yy年MM月dd日'),
            style: MyFontStyle.cardSubtitleStyleThemed(context),
          ),
        ],
        const Spacer(),
        _buildFavoriteButton(context),
        _buildShareButton(context),
      ],
    );
  }

  Widget _buildFavoriteButton(BuildContext context) {
    return IconButton(
      icon: Icon(
        article.isFavorite ? Icons.favorite : Icons.favorite_border,
        color: article.isFavorite ? AppColors.error(context) : AppColors.textSecondary(context),
      ),
      onPressed: () async {
        await ArticleService.i.toggleFavorite(article.id);
        controller.updateArticleInList(article.id);
      },
    );
  }

  Widget _buildShareButton(BuildContext context) {
    return IconButton(
      icon: Icon(Icons.share, color: AppColors.textSecondary(context)),
      onPressed: () {
        Share.share(article.url ?? '', subject: article.aiTitle ?? article.title ?? '');
      },
    );
  }
}
