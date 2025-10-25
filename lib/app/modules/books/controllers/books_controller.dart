import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/models/book_viewpoint.dart';
import 'dart:async';

/// 读书页面控制器
///
/// 负责管理读书页面的UI状态和用户交互
/// 数据管理由BooksStateService负责
class BooksController extends BaseController {
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

  // 自动刷新定时器
  Timer? _autoRefreshTimer;

  @override
  void onInit() {
    super.onInit();
    _initStateService();
    _booksStateService.loadAllViewpoints();
  }

  void _initStateService() {
    _booksStateService = Get.find<BooksStateService>();
  }

  @override
  void onClose() {
    scrollController.dispose();
    pageController.dispose();
    _autoRefreshTimer?.cancel();
    super.onClose();
  }

  /// 加载所有书籍
  List<BookModel> getAllBooks() {
    return _booksStateService.allBooks;
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    await _booksStateService.loadAllViewpoints();
    if (filterBookID.value == -1) {
      _touchShuffleTimeAndSchedule();
    } else {
      _cancelAutoRefreshIfAny();
    }
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
    final bookName = await DialogUtils.showInputDialog(
      title: '添加书籍',
      hintText: '请输入书名',
      confirmText: '添加',
      cancelText: '取消',
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
    if (filterBookID.value == -1) {
      _touchShuffleTimeAndSchedule();
    }
  }

  /// 内部:更新上次随机时间并安排自动刷新
  void _touchShuffleTimeAndSchedule() {
    _scheduleAutoRefresh();
  }

  void _scheduleAutoRefresh() {
    _cancelAutoRefreshIfAny();
    if (filterBookID.value != -1) return; // 仅在"所有/深链"模式下自动刷新

    // 12小时后自动刷新
    const duration = Duration(hours: 12);
    _autoRefreshTimer = Timer(duration, () async {
      try {
        await refreshRecommendations();
      } catch (_) {}
    });
  }

  void _cancelAutoRefreshIfAny() {
    _autoRefreshTimer?.cancel();
    _autoRefreshTimer = null;
  }
}
