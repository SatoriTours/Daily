import 'dart:io';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});
  @override
  Widget build(BuildContext context) {
    controller.loadArticles();

    return Scaffold(
      appBar: AppBar(
        title: const Text('收藏的文章'),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.settings),
          onPressed: () {
            Get.toNamed(Routes.SETTINGS);
          },
        ),
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
        return ListView.builder(
          itemCount: controller.articles.length,
          itemBuilder: (context, index) {
            final article = controller.articles[index];
            return ArticleCard(article: article);
          },
        );
      }),
    );
  }
}

class ArticleCard extends StatelessWidget {
  final dynamic article;

  const ArticleCard({super.key, required this.article});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 10, horizontal: 15),
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              (article.aiTitle ?? article.title),
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 5),
            Text(article.aiContent ?? article.content),
            const SizedBox(height: 5),
            if (article.imagePath != null && article.imagePath!.isNotEmpty)
              Container(
                constraints: BoxConstraints(
                  maxHeight: 500, // 设置最大高度为 500px
                ),
                width: double.infinity, // 设置宽度为无限
                child: Image.file(
                  File(article.imagePath!),
                  fit: BoxFit.fitWidth,
                  alignment: Alignment.topCenter,
                ),
              ),
            const SizedBox(height: 5),
            Text(
              '获取时间: ${article.pubDate ?? article.createdAt}',
              style: const TextStyle(color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }
}
