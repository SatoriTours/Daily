import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/models/book_viewpoint.dart';
import 'package:daily_satori/app/services/i18n/index.dart';

/// 读书页面控制器
///
/// 负责管理读书页面的UI状态和用户交互
/// 数据管理由BooksStateService负责
class BooksController extends BaseController with WidgetsBindingObserver {
  final scaffoldKey = GlobalKey<ScaffoldState>();

  /// 状态服务
  late final BooksStateService _booksStateService;

  /// 数据引用 - 引用自StateService
  RxList<BookViewpointModel> get allViewpoints => _booksStateService.viewpoints;
  RxInt get currentViewpointIndex => _booksStateService.currentViewpointIndex;
  RxInt get filterBookID => _booksStateService.filterBookID;
  RxBool get isProcessing => _booksStateService.isProcessing;
  RxBool get isLoadingBooks => _booksStateService.isLoading;

  // UI控制器
  final scrollController = ScrollController();
  final PageController pageController = PageController();

  // 上次刷新时间
  DateTime? _lastRefreshTime;

  // 刷新间隔（6小时）
  static const _refreshInterval = Duration(hours: 6);

  @override
  void onInit() {
    super.onInit();
    _initStateService();
    _booksStateService.loadAllViewpoints();
    WidgetsBinding.instance.addObserver(this);
    _lastRefreshTime = DateTime.now();
  }

  void _initStateService() {
    _booksStateService = Get.find<BooksStateService>();
  }

  @override
  void onClose() {
    WidgetsBinding.instance.removeObserver(this);
    scrollController.dispose();
    pageController.dispose();
    super.onClose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);

    // 当应用从后台恢复到前台时检查是否需要刷新
    if (state == AppLifecycleState.resumed) {
      _checkAndRefreshIfNeeded();
    }
  }

  /// 检查并在需要时刷新推荐列表
  void _checkAndRefreshIfNeeded() {
    // 只在"查看所有书籍"模式下自动刷新
    if (filterBookID.value != -1) return;

    // 检查上次刷新时间
    if (_lastRefreshTime == null) {
      _lastRefreshTime = DateTime.now();
      return;
    }

    final now = DateTime.now();
    final timeSinceLastRefresh = now.difference(_lastRefreshTime!);

    // 如果距离上次刷新超过6小时，则自动刷新
    if (timeSinceLastRefresh >= _refreshInterval) {
      logger.i('距离上次刷新已${timeSinceLastRefresh.inHours}小时，自动刷新推荐列表');
      refreshRecommendations();
    }
  }

  /// 加载所有书籍
  List<BookModel> getAllBooks() {
    return _booksStateService.allBooks;
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    await _booksStateService.loadAllViewpoints();
  }

  /// 选择书籍
  Future<void> selectBook(int bookID) async {
    await _booksStateService.selectBook(bookID);
    await loadAllViewpoints();
  }

  /// 获取当前观点
  BookViewpointModel? currentViewpoint() {
    return _booksStateService.currentViewpoint;
  }

  /// 显示添加书籍对话框
  Future<void> showAddBookDialog() async {
    final i18nService = I18nService.i;
    final bookName = await DialogUtils.showInputDialog(
      title: i18nService.translations.addBookTitle,
      hintText: i18nService.translations.addBookHint,
      confirmText: i18nService.translations.addBookConfirm,
      cancelText: i18nService.translations.addBookCancel,
    );

    if (bookName != null && bookName.trim().isNotEmpty) {
      await _addBookWithName(bookName.trim());
    }
  }

  /// 根据书名添加书籍
  Future<void> _addBookWithName(String bookName) async {
    try {
      final book = await _booksStateService.addBook(bookName);

      if (book != null) {
        await loadAllViewpoints();
        UIUtils.showSuccess('书籍《${book.title}》已添加到书架');
      } else {
        UIUtils.showError('无法添加书籍，请稍后重试');
      }
    } catch (e, stackTrace) {
      logger.e('添加书籍失败', error: e, stackTrace: stackTrace);
      UIUtils.showError('发生错误，请稍后重试');
    }
  }

  /// 删除书籍
  Future<void> deleteBook(int bookId) async {
    await _booksStateService.deleteBook(bookId);
  }

  /// 关闭抽屉
  void closeDrawer() {
    if (scaffoldKey.currentState?.isDrawerOpen == true) {
      scaffoldKey.currentState?.closeDrawer();
    }
  }

  /// 前往上一个观点
  void previousViewpoint() {
    if (currentViewpointIndex.value > 0) {
      _booksStateService.goToViewpointIndex(currentViewpointIndex.value - 1);
    }
  }

  /// 前往下一个观点
  void nextViewpoint() {
    if (currentViewpointIndex.value < allViewpoints.length - 1) {
      _booksStateService.goToViewpointIndex(currentViewpointIndex.value + 1);
    }
  }

  /// 刷新当前书籍内容（重新抓取信息与观点）
  Future<void> refreshBook(int bookId) async {
    try {
      final ok = await _booksStateService.refreshBook(bookId);
      if (!ok) return;
      await loadAllViewpoints();

      // 刷新后如果当前过滤的是该书或查看全部,则跳到该书第一条观点
      if (filterBookID.value == -1 || filterBookID.value == bookId) {
        final index = allViewpoints.indexWhere((v) => v.bookId == bookId);
        if (index >= 0) goToViewpointIndex(index);
      }
    } catch (_) {}
  }

  /// 通过观点ID打开并定位(用于深链跳转)
  Future<void> openViewpointById(int viewpointId) async {
    await _booksStateService.openViewpointById(viewpointId);
  }

  /// 前往指定的观点索引,并更新状态
  void goToViewpointIndex(int index, {bool animate = false}) {
    _booksStateService.goToViewpointIndex(index);

    if (!pageController.hasClients) {
      // 视图尚未挂载,延迟到下一帧尝试
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!pageController.hasClients) return;
        if (animate) {
          pageController.animateToPage(index, duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
        } else {
          pageController.jumpToPage(index);
        }
      });
      return;
    }

    if (animate) {
      pageController.animateToPage(index, duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
    } else {
      pageController.jumpToPage(index);
    }
  }

  // --- 刷新与随机相关 ---

  /// 手动刷新推荐列表
  Future<void> refreshRecommendations() async {
    await _booksStateService.refreshRecommendations();
    _lastRefreshTime = DateTime.now();
  }
}
