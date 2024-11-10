import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:drift/drift.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';

part 'part.clipboard.dart';
part 'part.article_load.dart';
part 'part.event.dart';
part 'part.update_list.dart';
part 'part.filter.dart';

class ArticlesController extends GetxController with WidgetsBindingObserver {
  ScrollController scrollController = ScrollController();
  DateTime lastRefreshTime = DateTime.now(); // 用来记录最后一次更新的时间, 当应用从后台回到前台的时候, 判断是否需要刷新
  TextEditingController searchController = TextEditingController();

  // 监听变化, 重绘页面的变量
  final List<Article> articles = <Article>[].obs;
  var isLoading = false.obs;
  var enableSearch = false.obs;

  @override
  void onInit() {
    super.onInit();
    _onInit();
  }

  @override
  void onClose() {
    _onClose();
    super.onClose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    await _didChangeAppLifecycleState(state);
  }

  Future<void> searchArticles() async {
    _searchText = searchController.text.trim();
    reloadArticles();
  }

  void toggleSearchState() {
    enableSearch.value = !enableSearch.value;
    if (!enableSearch.value) {
      _searchText = '';
      searchController.text = ''; // 清掉输入框里面的内容
      reloadArticles();
    }
  }

  void toggleOnlyFavorite(bool value) {
    _onlyFavorite = value;
    reloadArticles();
  }

  void showArticleByTagID(int tagID) {
    logger.i('设置标签ID: $tagID');
    _tagID = tagID;
    reloadArticles();
  }

  void showAllArticles() {
    _tagID = -1;
    _onlyFavorite = false;
    reloadArticles();
  }

  final _db = DBService.i.db;

  // -------------------------part 专用的变量-------------------------------

  // part 'clipboard.part.dart';
  String _clipboardText = ''; // 用户缓存剪切板里面的http链接内容,避免重复提醒

  // part 'part.filter.dart';
  String _searchText = '';
  bool _onlyFavorite = false;
  int _tagID = -1;

  // part 'part.article_load.dart';
  final int _pageSize = 20;
}
