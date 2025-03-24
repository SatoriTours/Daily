import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';

import 'article_card.dart';

/// 文章列表组件
class ArticlesList extends GetView<ArticlesController> {
  const ArticlesList({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return Obx(
      () => RefreshIndicator(
        onRefresh: () => controller.reloadArticles(),
        color: colorScheme.primary,
        child: ListView.separated(
          controller: controller.scrollController,
          itemCount: _calculateItemCount(),
          itemBuilder: (context, index) => _buildListItem(context, index),
          separatorBuilder: (context, index) => const SizedBox(height: 8),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        ),
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
    return ArticleCard(articleModel: controller.articles[index]);
  }

  Widget _buildLoadingIndicator(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

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
                valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
              ),
            ),
            const SizedBox(height: 12),
            Text('加载更多内容...', style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
          ],
        ),
      ),
    );
  }
}
