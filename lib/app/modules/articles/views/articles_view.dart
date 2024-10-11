import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/articles_controller.dart';

class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});
  @override
  Widget build(BuildContext context) {
    controller.loadArticles();

    return Scaffold(
      appBar: AppBar(
        title: const Text('ArticlesView'),
        centerTitle: true,
      ),
      body: Obx(() {
        if (controller.articles.isEmpty) {
          return const Center(
            child: CircularProgressIndicator(),
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

  const ArticleCard({Key? key, required this.article}) : super(key: key);

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
              article.title,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 5),
            Text(article.aiContent ?? article.content),
            const SizedBox(height: 5),
            if (article.imagePath != null && article.imagePath!.isNotEmpty)
              Image.asset(article.imagePath!),
            const SizedBox(height: 5),
            Text(
              '获取时间: ${article.pubDate ?? ""}',
              style: const TextStyle(color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }
}
