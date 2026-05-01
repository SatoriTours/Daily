import 'package:daily_satori/app_exports.dart';
import 'article_card.dart';

/// 文章列表组件
///
/// 自管理 ScrollController，提供下拉刷新和无限滚动功能
class ArticlesList extends StatefulWidget {
  final List<ArticleModel> articles;
  final bool isLoading;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onLoadMore;
  final void Function(ArticleModel article) onArticleTap;
  final void Function(ArticleModel article) onFavoriteToggle;
  final void Function(ArticleModel article) onShare;

  const ArticlesList({
    super.key,
    required this.articles,
    required this.isLoading,
    required this.onRefresh,
    required this.onLoadMore,
    required this.onArticleTap,
    required this.onFavoriteToggle,
    required this.onShare,
  });

  static void scrollToTop(BuildContext context) {
    final state = context.findAncestorStateOfType<_ArticlesListState>();
    state?._scrollToTop();
  }

  @override
  State<ArticlesList> createState() => _ArticlesListState();
}

class _ArticlesListState extends State<ArticlesList> {
  late final ScrollController _scrollController;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _setupScrollListener();
  }

  bool _isLoadingMore = false;

  void _setupScrollListener() {
    _scrollController.addListener(() {
      final position = _scrollController.position;
      // 距离底部 200px 时开始加载更多
      if (position.pixels >= position.maxScrollExtent - 200) {
        _loadMore();
      }
    });
  }

  Future<void> _loadMore() async {
    // 防止重复加载
    if (_isLoadingMore || widget.isLoading) return;
    if (widget.articles.isEmpty) return;

    _isLoadingMore = true;
    try {
      logger.i('Loading more articles...');
      await widget.onLoadMore();
    } finally {
      _isLoadingMore = false;
    }
  }

  void _scrollToTop() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(0, duration: Animations.durationNormal, curve: Curves.easeInOut);
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isEmpty = widget.articles.isEmpty && !widget.isLoading;

    if (isEmpty) {
      return _buildEmptyState();
    }

    return RefreshIndicator(
      onRefresh: widget.onRefresh,
      color: Theme.of(context).colorScheme.primary,
      child: _buildListView(),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.article_outlined, size: 64, color: Colors.grey[400]),
          const SizedBox(height: 16),
          Text('empty.no_articles'.t, style: AppTypography.titleMedium.copyWith(color: Colors.grey[600])),
        ],
      ),
    );
  }

  Widget _buildListView() {
    final articles = widget.articles;
    final isLoading = widget.isLoading;
    final itemCount = articles.length + (isLoading ? 1 : 0);

    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM - 4, vertical: Dimensions.spacingM - 4),
      itemCount: itemCount,
      itemBuilder: (context, index) {
        if (index == articles.length) {
          return _buildLoadingIndicator(context);
        }

        final article = articles[index];
        return Padding(
          padding: EdgeInsets.only(bottom: index < articles.length - 1 ? Dimensions.spacingS : 0),
          child: ArticleCard(
            key: ValueKey(article.id),
            articleModel: article,
            onTap: () => widget.onArticleTap(article),
            onFavoriteToggle: () => widget.onFavoriteToggle(article),
            onShare: () => widget.onShare(article),
          ),
        );
      },
    );
  }

  Widget _buildLoadingIndicator(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingL - 4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: Dimensions.spacingL,
            height: Dimensions.spacingL,
            child: CircularProgressIndicator(
              strokeWidth: 2.5,
              valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
            ),
          ),
          const SizedBox(height: 8),
          Text('article.loading_more'.t, style: textTheme.labelMedium?.copyWith(color: colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}
