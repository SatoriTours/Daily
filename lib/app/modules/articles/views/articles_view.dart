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
    return Container(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 120,
            height: 120,
            decoration: BoxDecoration(color: AppColors.primary(context).withOpacity(0.1), shape: BoxShape.circle),
            child: Icon(Icons.article_outlined, size: 60, color: AppColors.primary(context)),
          ),
          const SizedBox(height: 24),
          Text(
            '还没有收藏内容',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
          ),
          const SizedBox(height: 12),
          Text('您可以通过分享功能添加新文章', style: TextStyle(fontSize: 14, color: AppColors.textSecondary(context))),
          const SizedBox(height: 32),
          ElevatedButton.icon(
            onPressed: () {
              // 这里可以添加引导用户添加文章的逻辑
            },
            icon: const Icon(Icons.add),
            label: const Text('添加文章'),
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchTextField(BuildContext context) {
    return AnimatedSlide(
      offset: Offset(0, controller.enableSearch.value ? 0 : -1),
      duration: const Duration(milliseconds: 300),
      child: Container(
        height: 44,
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        child: TextField(
          controller: controller.searchController,
          decoration: InputDecoration(
            hintText: '搜索文章',
            hintStyle: TextStyle(fontSize: 14, color: AppColors.textSecondary(context)),
            prefixIcon: Icon(Icons.search, size: 20, color: AppColors.textSecondary(context)),
            suffixIcon: IconButton(
              icon: Icon(Icons.clear, size: 18, color: AppColors.textSecondary(context)),
              onPressed: () {
                controller.searchController.clear();
                controller.searchArticles();
              },
            ),
            filled: true,
            fillColor:
                Theme.of(context).brightness == Brightness.dark
                    ? AppColors.cardBackgroundDark.withOpacity(0.8)
                    : Colors.grey.shade100,
            contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
            enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(22),
              borderSide: BorderSide(color: AppColors.primary(context), width: 1),
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
      child: ListView.separated(
        controller: controller.scrollController,
        itemCount: _calculateItemCount(),
        itemBuilder: (context, index) => _buildListItem(context, index),
        separatorBuilder: (context, index) => const SizedBox(height: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      ),
    );
  }

  int _calculateItemCount() {
    return controller.articles.length + (controller.isLoading.value ? 1 : 0);
  }

  Widget _buildListItem(BuildContext context, int index) {
    if (index == controller.articles.length) {
      return _buildLoadingIndicator(context);
    }
    return ArticleCard(article: controller.articles[index]);
  }

  Widget _buildLoadingIndicator(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 20),
      child: Center(
        child: Column(
          children: [
            SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(
                strokeWidth: 2.5,
                valueColor: AlwaysStoppedAnimation<Color>(AppColors.primary(context)),
              ),
            ),
            const SizedBox(height: 12),
            Text('加载更多内容...', style: TextStyle(fontSize: 12, color: AppColors.textSecondary(context))),
          ],
        ),
      ),
    );
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
    return Text(
      article.aiTitle ?? article.title ?? '',
      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.textPrimary(context), height: 1.3),
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
                color: AppColors.divider(context),
                child: Icon(Icons.image_not_supported, color: AppColors.textSecondary(context)),
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
            article.isFavorite ? AppColors.error(context) : AppColors.textSecondary(context).withOpacity(0.7),
            () async {
              await ArticleService.i.toggleFavorite(article.id);
              controller.updateArticleInList(article.id);
            },
          ),
          const SizedBox(width: 8),
          _buildActionButton(context, Icons.share, AppColors.textSecondary(context).withOpacity(0.7), () {
            Share.share(article.url ?? '', subject: article.aiTitle ?? article.title ?? '');
          }),
        ],
      ),
    );
  }

  Widget _buildInfoItem(BuildContext context, IconData icon, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: AppColors.textSecondary(context).withOpacity(0.7)),
        const SizedBox(width: 4),
        Text(
          text,
          style: TextStyle(fontSize: 12, color: AppColors.textSecondary(context).withOpacity(0.7)),
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
