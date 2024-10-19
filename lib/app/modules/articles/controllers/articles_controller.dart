import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:sqflite/sqflite.dart';

import 'package:daily_satori/app/models/article.dart';
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
    final articleDataList = await ArticleService.instance.db.query(
      'articles',
      orderBy: 'id DESC',
      limit: pageSize,
    );
    articles.assignAll(
        articleDataList.map((data) => Article.fromMap(data)).toList());
  }

  Future<int> getMaxArticleId() async {
    final maxIdResult = await ArticleService.instance.db
        .rawQuery('SELECT MAX(id) FROM articles');
    return Sqflite.firstIntValue(maxIdResult) ?? -1;
  }

  Future<int> getMinArticleId() async {
    final minIdResult = await ArticleService.instance.db
        .rawQuery('SELECT MIN(id) FROM articles');
    return Sqflite.firstIntValue(minIdResult) ?? -1;
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
    int articleId = articles.first.id ?? -1;
    if (articleId == -1) {
      return;
    }
    if (articleId >= await getMaxArticleId()) {
      logger.i("没有比 $articleId 小的文章了");
      return;
    }
    isLoading.value = true;
    logger.i("获取 $articleId 之前的 $pageSize 个文章");
    try {
      final articleDataList = await ArticleService.instance.db.query(
        'articles',
        where: 'id > ?',
        whereArgs: [articleId],
        orderBy: 'id ASC',
        limit: pageSize,
      );
      var newArticles =
          articleDataList.map((data) => Article.fromMap(data)).toList();
      articles.insertAll(0, newArticles);
    } catch (e) {
      logger.d("获取 $articleId 之前的 $pageSize 个文章失败, $e");
    }

    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleId = articles.last.id ?? -1;
    if (articleId == -1) {
      return;
    }
    if (articleId <= await getMinArticleId()) {
      logger.i("没有比 $articleId 大的文章了");
      return;
    }
    isLoading.value = true;
    logger.i("获取 $articleId 之后的 $pageSize 个文章");
    try {
      final articleDataList = await ArticleService.instance.db.query(
        'articles',
        where: 'id < ?',
        whereArgs: [articleId],
        orderBy: 'id DESC',
        limit: pageSize,
      );
      var newArticles =
          articleDataList.map((data) => Article.fromMap(data)).toList();
      articles.addAll(newArticles);
    } catch (e) {
      logger.d("获取 $articleId 之后的 $pageSize 个文章失败, $e");
    }

    isLoading.value = false;
  }
}
