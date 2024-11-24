part of 'articles_controller.dart';

extension PartArticleLoad on ArticlesController {
  Future<void> reloadArticles() async {
    logger.i("重新加载文章");
    lastRefreshTime = DateTime.now();
    final newArticles = _queryByFilter();

    articles.assignAll(newArticles);
    if (scrollController.hasClients) scrollController.jumpTo(0);
  }

  Future<void> _loadPreviousArticles() async {
    int articleID = articles.first.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之前的 $_pageSize 个文章");
    final newArticles = _queryByFilter(Article_.id.greaterThan(articleID));

    articles.insertAll(0, newArticles);
    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleID = articles.last.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之后的 $_pageSize 个文章");
    final newArticles = _queryByFilter(Article_.id.lessThan(articleID));

    articles.addAll(newArticles);
    isLoading.value = false;
  }
}
