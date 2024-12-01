import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';
part 'part.clipboard.dart';
part 'part.article_load.dart';
part 'part.event.dart';
part 'part.update_list.dart';
part 'part.filter.dart';

class ArticlesController extends MyBaseController with WidgetsBindingObserver {
  ScrollController scrollController = ScrollController();
  DateTime lastRefreshTime = DateTime.now(); // 用来记录最后一次更新的时间, 当应用从后台回到前台的时候, 判断是否需要刷新
  TextEditingController searchController = TextEditingController();

  // 监听变化, 重绘页面的变量
  final List<Article> articles = <Article>[].obs;
  final isLoading = false.obs;
  final enableSearch = false.obs;
  final tagName = ''.obs;

  final articleBox = ObjectboxService.i.box<Article>();
  final tagBox = ObjectboxService.i.box<Tag>();

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
    _tagID = -1;
    _onlyFavorite.value = false;
    tagName.value = '';
    reloadArticles();
  }

  String appBarTitle() {
    var title = '文章';
    if (_onlyFavorite.value) title = '收藏的文章';
    if (tagName.value.isNotEmpty) title = "$title - ${tagName.value}";
    return title;
  }
  // -------------------------part 专用的变量-------------------------------

  // part 'clipboard.part.dart';
  String _clipboardText = ''; // 用户缓存剪切板里面的http链接内容,避免重复提醒

  // part 'part.filter.dart';
  String _searchText = '';
  final _onlyFavorite = false.obs;
  int _tagID = -1;

  // part 'part.article_load.dart';
  final int _pageSize = 20;
}
