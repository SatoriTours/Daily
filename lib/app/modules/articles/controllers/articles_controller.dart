import 'package:daily_satori/app/databases/articles.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';

class ArticlesController extends GetxController with WidgetsBindingObserver {
  static int get pageSize => isProduction ? 20 : 5;
  ScrollController scrollController = ScrollController();
  DateTime lastRefreshTime = DateTime.now(); // 用来记录最后一次更新的时间, 当应用从后台回到前台的时候, 判断是否需要刷新
  TextEditingController searchController = TextEditingController();

  final List<Article> articles = <Article>[].obs;
  var isLoading = false.obs;
  var enableSearch = false.obs;

  String _searchText = '';

  @override
  void onInit() {
    super.onInit();
    scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void onClose() {
    scrollController.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.onClose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed) {
      logger.i("App is back from background");
      if (scrollController.position.pixels <= 30 || DateTime.now().difference(lastRefreshTime).inMinutes >= 60) {
        reloadArticles();
      }
    }
  }

  void removeArticleByIdFromList(int id) {
    articles.removeWhere((article) => article.id == id);
  }

  void updateArticleFromList(Article updatedArticle) {
    int index = articles.indexWhere((article) => article.id == updatedArticle.id);
    if (index != -1) {
      articles[index] = updatedArticle; // 更新文章
      logger.i("文章已更新: ${updatedArticle.title}");
    } else {
      logger.i("未找到要更新的文章: ${updatedArticle.id}");
    }
  }

  Future<void> reloadArticles() async {
    logger.i("重新加载文章");
    lastRefreshTime = DateTime.now();
    var select = ArticleService.i.getArticles();
    if (enableSearch.value) {
      // select.where((u) => u.title.like("%${searchController.text}%"));
    }
    articles.assignAll(await select.get());
  }

  Future<void> searchArticles() async {
    if (searchController.text.isNotEmpty) {
      _searchText = searchController.text;
      reloadArticles();
    }
  }

  void _onScroll() {
    if (scrollController.position.pixels == scrollController.position.maxScrollExtent) {
      _loadMoreArticles();
    } else if (scrollController.position.pixels == scrollController.position.minScrollExtent) {
      _loadPreviousArticles();
    }
  }

  Future<void> _loadPreviousArticles() async {
    int articleID = articles.first.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之前的 $pageSize 个文章");
    final newArticles = await ArticleService.i.getArticlesGreaterThanId(articleID, limit: pageSize);
    articles.insertAll(0, newArticles);
    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleID = articles.last.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之后的 $pageSize 个文章");
    final newArticles = await ArticleService.i.getArticlesLessThanId(articleID, limit: pageSize);
    articles.addAll(newArticles);
    isLoading.value = false;
  }
}
