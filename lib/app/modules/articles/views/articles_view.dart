import 'dart:io';

import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';
import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/global.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(),
      body: _buildBody(),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      title: Obx(() => Text(
            controller.appBarTitle(),
            style: MyFontStyle.appBarTitleStyle,
          )),
      centerTitle: true,
      leading: IconButton(
        icon: const Icon(Icons.menu),
        onPressed: () => Get.toNamed(Routes.LEFT_BAR),
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.search),
          onPressed: controller.toggleSearchState,
        ),
      ],
    );
  }

  Widget _buildBody() {
    return Obx(() {
      if (controller.articles.isEmpty) {
        return _buildEmptyView();
      }
      return Column(
        children: [
          if (controller.enableSearch.value) _buildSearchTextField(),
          Expanded(child: _buildArticlesList()),
        ],
      );
    });
  }

  Widget _buildEmptyView() {
    return const Center(
      child: Text(
        '还没有收藏内容',
        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
      ),
    );
  }

  Widget _buildSearchTextField() {
    return AnimatedSlide(
      offset: Offset(0, controller.enableSearch.value ? 0 : -1),
      duration: const Duration(milliseconds: 300),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: TextField(
          controller: controller.searchController,
          decoration: const InputDecoration(
            border: OutlineInputBorder(),
            hintText: '搜索文章',
          ),
          onSubmitted: (_) => controller.searchArticles(),
        ),
      ),
    );
  }

  Widget _buildArticlesList() {
    return RefreshIndicator(
      onRefresh: controller.reloadArticles,
      child: ListView.builder(
        controller: controller.scrollController,
        itemCount: _calculateItemCount(),
        itemBuilder: _buildListItem,
      ),
    );
  }

  int _calculateItemCount() {
    return controller.articles.length + (controller.isLoading.value ? 1 : 0);
  }

  Widget _buildListItem(BuildContext context, int index) {
    if (index == controller.articles.length) {
      return const Center(child: CircularProgressIndicator());
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
      margin: const EdgeInsets.symmetric(vertical: 10, horizontal: 15),
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Column(
          children: [
            _buildArticleContent(),
            const SizedBox(height: 5),
            _buildActionBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildArticleContent() {
    final imagePath = article.images.isEmpty ? '' : article.images.first.path ?? '';

    return GestureDetector(
      onTap: () => Get.toNamed(Routes.ARTICLE_DETAIL, arguments: article),
      child: Row(
        children: [
          Expanded(child: _buildTitle()),
          if (imagePath.isNotEmpty) ...[
            const SizedBox(width: 15),
            _buildImage(imagePath),
          ],
        ],
      ),
    );
  }

  Widget _buildTitle() {
    return Text(
      article.aiTitle ?? article.title ?? '',
      style: MyFontStyle.listTitleStyle,
    );
  }

  Widget _buildImage(String path) {
    return SizedBox(
      width: 100,
      height: 100,
      child: Image.file(
        File(path),
        fit: BoxFit.scaleDown,
        alignment: Alignment.center,
        errorBuilder: (_, __, ___) => const SizedBox.shrink(),
      ),
    );
  }

  Widget _buildActionBar() {
    final url = Uri.parse(article.url ?? '');

    return Row(
      children: [
        Text(
          getTopLevelDomain(url.host),
          style: const TextStyle(color: Colors.grey),
        ),
        const SizedBox(width: 15),
        if (article.createdAt != null)
          Text(
            GetTimeAgo.parse(article.createdAt!, pattern: 'yy年MM月dd日'),
            style: const TextStyle(color: Colors.grey),
          ),
        const Spacer(),
        _buildFavoriteButton(),
        _buildShareButton(),
      ],
    );
  }

  Widget _buildFavoriteButton() {
    return IconButton(
      icon: Icon(article.isFavorite ? Icons.favorite : Icons.favorite_border),
      onPressed: () async {
        await ArticleService.i.toggleFavorite(article.id);
        await controller.updateArticleInListFromDB(article.id);
      },
    );
  }

  Widget _buildShareButton() {
    return IconButton(
      icon: const Icon(Icons.share),
      onPressed: () {
        Share.share(
          article.url ?? '',
          subject: article.aiTitle ?? article.title ?? '',
        );
      },
    );
  }
}
