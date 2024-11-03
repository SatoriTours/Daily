part of 'articles_controller.dart';

extension PartArticleLoad on ArticlesController {
  Future<void> reloadArticles() async {
    logger.i("重新加载文章");
    lastRefreshTime = DateTime.now();
    final newArticles = ArticleService.i.getArticles();
    _addFilterExpression(newArticles);
    articles.assignAll(await newArticles.get());
    scrollController.jumpTo(0);
  }

  Future<void> _loadPreviousArticles() async {
    int articleID = articles.first.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之前的 $_pageSize 个文章");
    final newArticles = ArticleService.i.getArticlesGreaterThanId(articleID, limit: _pageSize);
    _addFilterExpression(newArticles);
    articles.insertAll(0, await newArticles.get());
    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleID = articles.last.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之后的 $_pageSize 个文章");
    final newArticles = ArticleService.i.getArticlesLessThanId(articleID, limit: _pageSize);
    _addFilterExpression(newArticles);
    articles.addAll(await newArticles.get());
    isLoading.value = false;
  }
}
