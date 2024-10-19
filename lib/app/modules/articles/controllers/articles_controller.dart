import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';

class ArticlesController extends GetxController {
  static int get pageSize => isProduction ? 20 : 5;

  final List<Article> articles = <Article>[].obs;
  ScrollController scrollController = ScrollController();
  var isLoading = false.obs; // 加载状态

  @override
  void onInit() {
    super.onInit();
    scrollController.addListener(_onScroll);
  }

  @override
  void onClose() {
    scrollController.dispose();
    super.onClose();
  }

  Future<void> reloadArticles() async {
    logger.i("重新加载文章");
    final newArticles = await ArticleService.i.getArticles();
    articles.assignAll(newArticles);
  }

  void _onScroll() {
    if (scrollController.position.pixels ==
        scrollController.position.maxScrollExtent) {
      _loadMoreArticles();
    } else if (scrollController.position.pixels ==
        scrollController.position.minScrollExtent) {
      _loadPreviousArticles();
    }
  }

  Future<void> _loadPreviousArticles() async {
    int articleID = articles.first.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之前的 $pageSize 个文章");
    final newArticles = await ArticleService.i
        .getArticlesGreaterThanId(articleID, limit: pageSize);
    articles.insertAll(0, newArticles);
    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleID = articles.last.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之后的 $pageSize 个文章");
    final newArticles = await ArticleService.i
        .getArticlesLessThanId(articleID, limit: pageSize);
    articles.addAll(newArticles);
    isLoading.value = false;
  }
}
