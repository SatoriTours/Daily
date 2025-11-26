import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/data/article/article_model.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'article_card.dart';

/// 文章列表组件
///
/// 纯展示组件,负责展示文章列表和加载状态
/// 通过回调函数与外部交互
class ArticlesList extends StatelessWidget {
  final List<ArticleModel> articles;
  final bool isLoading;
  final ScrollController scrollController;
  final Future<void> Function() onRefresh;
  final void Function(ArticleModel article) onArticleTap;
  final void Function(ArticleModel article) onFavoriteToggle;
  final void Function(ArticleModel article) onShare;

  const ArticlesList({
    super.key,
    required this.articles,
    required this.isLoading,
    required this.scrollController,
    required this.onRefresh,
    required this.onArticleTap,
    required this.onFavoriteToggle,
    required this.onShare,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      color: AppTheme.getColorScheme(context).primary,
      child: _buildListView(),
    );
  }

  /// 构建列表视图
  Widget _buildListView() {
    final itemCount = _calculateItemCount();

    return ListView.builder(
      controller: scrollController,
      itemCount: itemCount,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      itemBuilder: (context, index) {
        if (index == articles.length) {
          return _buildLoadingIndicator(context);
        }

        final article = articles[index];
        return Padding(
          padding: EdgeInsets.only(bottom: index < articles.length - 1 ? 8.0 : 0),
          child: ArticleCard(
            key: ValueKey(article.id),
            articleModel: article,
            onTap: () => onArticleTap(article),
            onFavoriteToggle: () => onFavoriteToggle(article),
            onShare: () => onShare(article),
          ),
        );
      },
    );
  }

  /// 计算列表项总数
  int _calculateItemCount() {
    return articles.length + (isLoading ? 1 : 0);
  }

  /// 构建加载指示器
  Widget _buildLoadingIndicator(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    return Container(
      padding: const EdgeInsets.symmetric(vertical: 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
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
          Text('article.loading_more'.t, style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}
