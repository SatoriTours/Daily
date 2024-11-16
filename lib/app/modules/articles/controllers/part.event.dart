part of 'articles_controller.dart';

extension PartEvent on ArticlesController {
  void _onInit() {
    scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addObserver(this);
    reloadArticles();
    checkClipboardText(); // 检查剪切板里面是否有http开头的链接, 如果是的就确认是否保存
    AppUpgradeService.i.checkAndDownloadInbackend(); // 检查是否有更新
  }

  void _onClose() {
    scrollController.dispose();
    WidgetsBinding.instance.removeObserver(this);
  }

  Future<void> _didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed) {
      logger.i("App is back from background");
      if (scrollController.hasClients) {
        if (scrollController.position.pixels <= 30 || DateTime.now().difference(lastRefreshTime).inMinutes >= 60) {
          reloadArticles();
        }
      }
      checkClipboardText(); // 检查剪切板里面是否有http开头的链接, 如果是的就确认是否保存
    }
  }

  void _onScroll() {
    if (scrollController.position.pixels == scrollController.position.maxScrollExtent) {
      _loadMoreArticles();
    } else if (scrollController.position.pixels == scrollController.position.minScrollExtent) {
      _loadPreviousArticles();
    }
  }
}
