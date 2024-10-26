import 'dart:io';

import 'package:daily_satori/app/databases/database.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:get_time_ago/get_time_ago.dart';
import 'package:share_plus/share_plus.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/global.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});
  @override
  Widget build(BuildContext context) {
    controller.reloadArticles();

    return Scaffold(
      appBar: AppBar(
        title: Text(
          '收藏的文章',
          style: MyFontStyle.appBarTitleStyle,
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.settings),
          onPressed: () {
            Get.toNamed(Routes.SETTINGS);
          },
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.search),
            onPressed: () {
              controller.toggleSearchState();
            },
          ),
        ],
      ),
      body: Obx(() {
        if (controller.articles.isEmpty) {
          return const Center(
            child: Text(
              '还没有收藏内容',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          );
        }
        return Column(
          children: [
            if (controller.enableSearch.value) _buildSearchTextField(),
            Expanded(child: _buildArticlesList()),
          ],
        );
      }),
    );
  }

  Widget _buildSearchTextField() {
    return AnimatedSlide(
      offset: controller.enableSearch.value ? Offset(0, 0) : Offset(0, -1),
      duration: const Duration(milliseconds: 300),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 0),
        child: TextField(
          controller: controller.searchController,
          decoration: InputDecoration(
            border: OutlineInputBorder(),
            hintText: '搜索文章',
          ),
          onSubmitted: (value) {
            controller.searchArticles();
          },
        ),
      ),
    );
  }

  Widget _buildArticlesList() {
    return RefreshIndicator(
      onRefresh: () async {
        await controller.reloadArticles(); // 下拉刷新时重新加载文章
      },
      child: ListView.builder(
        controller: controller.scrollController,
        itemCount: controller.articles.length + (controller.isLoading.value ? 1 : 0), // 如果正在加载，增加一个加载项
        itemBuilder: (context, index) {
          if (index == controller.articles.length) {
            return Center(child: CircularProgressIndicator());
          }
          final article = controller.articles[index];
          return ArticleCard(article: article);
        },
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
      margin: const EdgeInsets.symmetric(vertical: 10, horizontal: 15),
      child: _buildArticle(),
    );
  }

  Widget _buildArticle() {
    return Padding(
      padding: const EdgeInsets.all(10),
      child: Column(
        children: [
          GestureDetector(
            onTap: () {
              Get.toNamed(Routes.ARTICLE_DETAIL, arguments: article);
            },
            child: Row(
              children: [
                Expanded(
                  child: _buildTitle(),
                ),
                const SizedBox(width: 15),
                if (article.imagePath != null && article.imagePath!.isNotEmpty) _buildImage(),
              ],
            ),
          ),
          const SizedBox(height: 5),
          _buildActionBar(),
        ],
      ),
    );
  }

  Widget _buildTitle() {
    return Text(
      (article.aiTitle ?? article.title ?? ''),
      style: MyFontStyle.listTitleStyle,
    );
  }

  Widget _buildImage() {
    return Image.file(
      File(article.imagePath!),
      width: 100, // 设置宽度为100
      height: 100, // 设置高度为80
      fit: BoxFit.scaleDown, // 适应容器
      alignment: Alignment.center,
      errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) {
        // 图片加载错误, 就什么都不显示
        return SizedBox.shrink();
      },
    );
  }

  Widget _buildActionBar() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          getTopLevelDomain(Uri.parse(article.url).host),
          style: const TextStyle(color: Colors.grey),
        ),
        const SizedBox(width: 15),
        Text(
          (GetTimeAgo.parse(article.createdAt)),
          style: const TextStyle(color: Colors.grey),
        ),
        Spacer(), // 使用 Spacer 使按钮靠右对齐
        IconButton(
          icon: Icon(
            article.isFavorite ? Icons.favorite : Icons.favorite_border,
          ),
          onPressed: () async {
            await ArticleService.i.toggleFavorite(article.id);
            await controller.updateArticleInListFromDB(article.id);
          },
        ),
        IconButton(
          icon: const Icon(Icons.share),
          onPressed: () {
            Share.share(article.url, subject: article.aiTitle ?? article.title);
          },
        ),
      ],
    );
  }
}
