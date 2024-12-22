import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

part 'part.clipboard.dart';
part 'part.article_load.dart';
part 'part.event.dart';
part 'part.update_list.dart';
part 'part.filter.dart';

class ArticlesController extends MyBaseController with WidgetsBindingObserver {
  // UI 控制器
  final scrollController = ScrollController();
  final searchController = TextEditingController();

  // 可观察状态
  final articles = <Article>[].obs;
  final isLoading = false.obs;
  final enableSearch = false.obs;
  final tagName = ''.obs;
  final _onlyFavorite = false.obs;

  // 数据源
  final articleBox = ObjectboxService.i.box<Article>();
  final tagBox = ObjectboxService.i.box<Tag>();

  // 内部状态
  DateTime lastRefreshTime = DateTime.now(); // 记录最后更新时间,用于后台恢复时判断是否需要刷新
  String _clipboardText = ''; // 缓存剪切板链接,避免重复提醒
  String _searchText = '';
  int _tagID = -1;
  final int _pageSize = 20;

  // 生命周期方法
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

  // 搜索相关方法
  Future<void> searchArticles() async {
    _searchText = searchController.text.trim();
    reloadArticles();
  }

  void toggleSearchState() {
    enableSearch.value = !enableSearch.value;
    if (!enableSearch.value) {
      _clearSearch();
    }
  }

  void _clearSearch() {
    _searchText = '';
    searchController.text = '';
    reloadArticles();
  }

  // 筛选相关方法
  void toggleOnlyFavorite(bool value) {
    _onlyFavorite.value = value;
    reloadArticles();
  }

  void showArticleByTagID(int tagID, String tagName) {
    logger.i('设置标签ID: $tagID');
    _tagID = tagID;
    this.tagName.value = tagName;
    reloadArticles();
  }

  void showAllArticles() {
    _resetFilters();
    reloadArticles();
  }

  void _resetFilters() {
    _tagID = -1;
    _onlyFavorite.value = false;
    tagName.value = '';
  }

  // UI 辅助方法
  String appBarTitle() {
    var title = '文章';
    if (_onlyFavorite.value) title = '收藏的文章';
    if (tagName.value.isNotEmpty) title = "$title - ${tagName.value}";
    return title;
  }
}
