import 'package:flutter/material.dart';

import 'package:daily_satori/app/components/cards/article_card.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/global.dart';

/// 文章列表组件
class ArticlesList extends StatelessWidget {
  final List<ArticleModel> articles;
  final ScrollController? scrollController;
  final Future<void> Function()? onRefresh;
  final bool isLoading;
  final VoidCallback? onArticleUpdated;

  const ArticlesList({
    super.key,
    required this.articles,
    this.scrollController,
    this.onRefresh,
    this.isLoading = false,
    this.onArticleUpdated,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);

    return RefreshIndicator(
      onRefresh: onRefresh ?? () async {},
      color: colorScheme.primary,
      child: ListView.separated(
        controller: scrollController,
        itemCount: _calculateItemCount(),
        itemBuilder: (context, index) => _buildListItem(context, index),
        separatorBuilder: (context, index) => const SizedBox(height: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      ),
    );
  }

  int _calculateItemCount() {
    return articles.length + (isLoading ? 1 : 0);
  }

  Widget _buildListItem(BuildContext context, int index) {
    if (index == articles.length) {
      return _buildLoadingIndicator(context);
    }
    return ArticleCard(articleModel: articles[index], onArticleUpdated: onArticleUpdated);
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
