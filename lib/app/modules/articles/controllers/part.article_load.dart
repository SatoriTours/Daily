part of 'articles_controller.dart';

extension PartArticleLoad on ArticlesController {
  Future<void> reloadArticles() async {
    logger.i("重新加载文章");
    lastRefreshTime = DateTime.now();
    final tr = DBService.i.startTransaction('ArticlesController', 'reloadArticles');
    final newArticles = _addFilterExpression();

    articles.assignAll(await _getArticles(newArticles));
    DBService.i.stopTransaction(tr);
    if (scrollController.hasClients) scrollController.jumpTo(0);
  }

  Future<void> _loadPreviousArticles() async {
    int articleID = articles.first.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之前的 $_pageSize 个文章");
    final newArticles = _addFilterExpression();
    newArticles.where((t) => t.id.isBiggerThanValue(articleID));

    articles.insertAll(0, await _getArticles(newArticles));
    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleID = articles.last.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之后的 $_pageSize 个文章");
    final newArticles = _addFilterExpression();
    newArticles.where((t) => t.id.isSmallerThanValue(articleID));

    articles.addAll(await _getArticles(newArticles));
    isLoading.value = false;
  }
}
