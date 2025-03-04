part of 'articles_controller.dart';

extension PartArticleLoad on ArticlesController {
  /// 重新加载文章列表
  Future<void> reloadArticles() async {
    logger.i('重新加载文章列表');
    lastRefreshTime = DateTime.now();

    final newArticles = _queryByFilter();
    articleModels.assignAll(newArticles);

    // 滚动到顶部
    if (scrollController.hasClients) {
      scrollController.jumpTo(0);
    }
  }

  /// 加载更早的文章
  Future<void> _loadPreviousArticles() async {
    if (articleModels.isEmpty) return;

    isLoading.value = true;
    try {
      final articleID = articleModels.first.id;
      logger.i('加载ID:$articleID之前的$_pageSize篇文章');

      final newArticles = _queryByFilter(articleID, true);
      articleModels.insertAll(0, newArticles);
    } finally {
      isLoading.value = false;
    }
  }

  /// 加载更多文章
  Future<void> _loadMoreArticles() async {
    if (articleModels.isEmpty) return;

    isLoading.value = true;
    try {
      final articleID = articleModels.last.id;
      logger.i('加载ID:$articleID之后的$_pageSize篇文章');

      final newArticles = _queryByFilter(articleID, false);
      articleModels.addAll(newArticles);
    } finally {
      isLoading.value = false;
    }
  }
}
