import 'package:flutter/material.dart';

import 'package:daily_satori/app/components/cards/article_card.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 文章列表组件
class ArticlesList extends StatelessWidget {
  final List<Article> articles;
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
    return RefreshIndicator(
      onRefresh: onRefresh ?? () async {},
      color: AppColors.primary(context),
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
    return ArticleCard(article: articles[index], onArticleUpdated: onArticleUpdated);
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
