part of 'articles_controller.dart';

extension PartEvent on ArticlesController {
  /// 初始化控制器
  void _onInit() {
    _initScrollListener();
    _initLifecycleObserver();
    _initData();
  }

  void _initScrollListener() {
    scrollController.addListener(_onScroll);
  }

  void _initLifecycleObserver() {
    WidgetsBinding.instance.addObserver(this);
  }

  void _initData() {
    reloadArticles();
    checkClipboardText(); // 检查剪切板链接
    AppUpgradeService.i.checkAndDownloadInbackend(); // 检查更新
  }

  /// 释放资源
  void _onClose() {
    scrollController.dispose();
    WidgetsBinding.instance.removeObserver(this);
  }

  /// 处理应用生命周期变化
  Future<void> _didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state != AppLifecycleState.resumed) return;

    logger.i("应用从后台恢复");
    await _handleAppResume();
  }

  Future<void> _handleAppResume() async {
    if (!scrollController.hasClients) return;

    final shouldReload = _shouldReloadArticles();
    if (shouldReload) {
      await reloadArticles();
    }

    await checkClipboardText();
  }

  bool _shouldReloadArticles() {
    final isAtTop = scrollController.position.pixels <= 30;
    final hasBeenLongTime = DateTime.now().difference(lastRefreshTime).inMinutes >= 60;
    return isAtTop || hasBeenLongTime;
  }

  /// 处理滚动事件
  void _onScroll() {
    final position = scrollController.position;

    if (position.pixels == position.maxScrollExtent) {
      _loadMoreArticles();
    } else if (position.pixels == position.minScrollExtent) {
      _loadPreviousArticles();
    }
  }
}
