import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:drift/drift.dart';
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

    WidgetsBinding.instance.addPostFrameCallback((_) {
      AppUpgradeService.i.checkAndDownloadInbackend();
    });

    reloadArticles();
    checkClipboardText();
  }

  @override
  void onClose() {
    scrollController.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.onClose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed && scrollController.hasClients) {
      logger.i("App is back from background");
      if (scrollController.position.pixels <= 30 || DateTime.now().difference(lastRefreshTime).inMinutes >= 60) {
        reloadArticles();
      }
      checkClipboardText(); // 检查剪切板里面是否有http开头的链接, 如果是的就确认是否保存
    }
  }

  void removeArticleByIdFromList(int id) {
    articles.removeWhere((article) => article.id == id);
  }

  void updateArticleInList(Article updatedArticle) {
    int index = articles.indexWhere((article) => article.id == updatedArticle.id);
    if (index != -1) {
      articles[index] = updatedArticle; // 更新文章
      logger.i("文章已更新: ${updatedArticle.title}");
    } else {
      logger.i("未找到要更新的文章: ${updatedArticle.id}");
    }
  }

  Future<void> updateArticleInListFromDB(int articleID) async {
    int index = articles.indexWhere((article) => article.id == articleID);
    if (index != -1) {
      final newArticle = await ArticleService.i.getArticleById(articleID);
      articles[index] = newArticle; // 更新文章
      logger.i("文章已更新: $articleID");
    } else {
      logger.i("未找到要更新的文章: $articleID");
    }
  }

  Future<void> reloadArticles() async {
    logger.i("重新加载文章");
    lastRefreshTime = DateTime.now();
    final newArticles = ArticleService.i.getArticles();
    addSearchExpression(newArticles);
    articles.assignAll(await newArticles.get());
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

  void addSearchExpression(SimpleSelectStatement<$ArticlesTable, Article> select) {
    if (enableSearch.value && _searchText.isNotEmpty) {
      final searchExpression = "%$_searchText%";
      // 使用 where 条件的时候,头文件需要包含 import 'package:drift/drift.dart'; 不然会报错找不到 like 方法
      select.where((t) {
        return t.title.like(searchExpression) |
            t.aiTitle.like(searchExpression) |
            t.content.like(searchExpression) |
            t.aiContent.like(searchExpression);
      });
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
    final newArticles = ArticleService.i.getArticlesGreaterThanId(articleID, limit: pageSize);
    addSearchExpression(newArticles);
    articles.insertAll(0, await newArticles.get());
    isLoading.value = false;
  }

  Future<void> _loadMoreArticles() async {
    int articleID = articles.last.id;
    isLoading.value = true;
    logger.i("获取 $articleID 之后的 $pageSize 个文章");
    final newArticles = ArticleService.i.getArticlesLessThanId(articleID, limit: pageSize);
    addSearchExpression(newArticles);
    articles.addAll(await newArticles.get());
    isLoading.value = false;
  }

  String _clipboardText = '';
  void checkClipboardText() {
    logger.i("[checkClipboardText] 检查剪切板里面是否包含http开头的链接");
    getClipboardText().then((String url) {
      logger.i("[checkClipboardText] 读取剪切板内容 $url");
      if (url.startsWith('http') && url != _clipboardText) {
        showConfirmationDialog(
          '是否保存',
          '获取到剪切板链接:\n${getSubstring(url, length: 30, suffix: '...')}\n\n请确认是否保存?',
          onConfirmed: () async {
            await setClipboardText('');
            Get.toNamed(Routes.SHARE_DIALOG, arguments: {'shareURL': url});
            _clipboardText = url;
          },
          onCanceled: () => _clipboardText = url,
        );
      }
    });
  }
}
