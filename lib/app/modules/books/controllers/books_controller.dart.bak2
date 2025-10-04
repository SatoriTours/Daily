import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';
import 'dart:async';
import 'dart:math';

/// 读书页面控制器
///
/// 负责管理读书页面的状态和交互逻辑
class BooksController extends BaseController {
  final BookService _bookService = BookService.i;
  final scaffoldKey = GlobalKey<ScaffoldState>();

  // 所有观点和当前观点
  final allViewpoints = <BookViewpointModel>[].obs;
  final currentViewpointIndex = 0.obs;

  // 当前选中的书籍
  final filterBookID = (-1).obs;

  final isProcessing = false.obs;

  // 滚动控制器
  final scrollController = ScrollController();
  // 读书观点翻页控制器（持久化，避免 initialPage 不生效问题）
  final PageController pageController = PageController();

  // 随机推荐相关：模式、种子、时间与定时器
  DisplayMode _mode = DisplayMode.allRandom; // 默认：所有书籍随机10条
  int? _deepLinkSeedViewpointId; // 深链模式下需要优先展示的观点
  Timer? _autoRefreshTimer; // 12小时自动刷新定时器
  final Random _rand = Random();
  static const int _kRandomCount = 10;

  @override
  void onInit() {
    super.onInit();
    loadAllViewpoints();
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
    return _bookService.getBooks();
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    logger.i('加载观点: bookID: ${filterBookID.value}, mode: $_mode');

    if (filterBookID.value == -1) {
      await _loadAllBooksViewpoints();
    } else {
      await _loadSpecificBookViewpoints();
    }
  }

  /// 加载所有书籍的观点（随机模式）
  Future<void> _loadAllBooksViewpoints() async {
    final allViewpoints = await BookRepository.getAllViewpointsAsync();
    if (allViewpoints.isEmpty) {
      this.allViewpoints.clear();
      return;
    }

    _setRandomModeViewpoints(allViewpoints);
    goToViewpointIndex(0);
    _touchShuffleTimeAndSchedule();
  }

  /// 根据模式设置随机观点
  void _setRandomModeViewpoints(List<BookViewpointModel> allViewpoints) {
    if (_mode == DisplayMode.deepLinkMix && _deepLinkSeedViewpointId != null) {
      this.allViewpoints.value = _buildDeepLinkMix(allViewpoints, _deepLinkSeedViewpointId!);
    } else {
      _mode = DisplayMode.allRandom; // 兜底为全量随机
      this.allViewpoints.value = _pickRandom(allViewpoints, _kRandomCount);
    }
  }

  /// 加载特定书籍的观点
  Future<void> _loadSpecificBookViewpoints() async {
    _mode = DisplayMode.bookSpecific;
    allViewpoints.value = await BookRepository.getViewpointsByBookIdsAsync([filterBookID.value]);

    if (allViewpoints.isEmpty) {
      await _tryRefreshBookViewpoints();
    }

    if (allViewpoints.isNotEmpty) {
      goToViewpointIndex(0);
    }
    _cancelAutoRefreshIfAny();
  }

  /// 尝试重新获取书籍观点
  Future<void> _tryRefreshBookViewpoints() async {
    logger.i('书籍 ${filterBookID.value} 没有观点，尝试重新获取...');
    try {
      final success = await _bookService.refreshBook(filterBookID.value);
      if (success) {
        allViewpoints.value = await BookRepository.getViewpointsByBookIdsAsync([filterBookID.value]);
        logger.i('重新获取书籍观点成功，共 ${allViewpoints.length} 条');
      }
    } catch (e) {
      logger.e('重新获取书籍观点失败', error: e);
    }
  }

  /// 选择书籍
  Future<void> selectBook(int bookID) async {
    filterBookID.value = bookID;
    // 切换筛选时重置模式与种子
    if (bookID == -1) {
      _mode = DisplayMode.allRandom;
      _deepLinkSeedViewpointId = null;
    } else {
      _mode = DisplayMode.bookSpecific;
      _deepLinkSeedViewpointId = null;
    }
  }

  BookViewpointModel? currentViewpoint() {
    if (allViewpoints.isEmpty) return null;
    final index = currentViewpointIndex.value.clamp(0, allViewpoints.length - 1);
    return allViewpoints[index];
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
      isProcessing.value = true;
      await _addBookWithName(bookName.trim());
      isProcessing.value = false;
    }
  }

  /// 根据书名添加书籍
  Future<void> _addBookWithName(String bookName) async {
    try {
      final book = await _bookService.addBook(bookName);

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
    await _bookService.deleteBook(bookId);
    await loadAllViewpoints();
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
      currentViewpointIndex.value--;
    }
  }

  /// 前往下一个观点
  void nextViewpoint() {
    if (currentViewpointIndex.value < allViewpoints.length - 1) {
      currentViewpointIndex.value++;
    }
  }

  /// 刷新当前书籍内容（重新抓取信息与观点）
  Future<void> refreshBook(int bookId) async {
    try {
      final ok = await _bookService.refreshBook(bookId);
      if (!ok) return;
      await loadAllViewpoints();

      // 刷新后如果当前过滤的是该书或查看全部，则跳到该书第一条观点
      if (filterBookID.value == -1 || filterBookID.value == bookId) {
        final index = allViewpoints.indexWhere((v) => v.bookId == bookId);
        if (index >= 0) currentViewpointIndex.value = index;
      }
    } catch (_) {}
  }

  /// 通过观点ID打开并定位（用于深链跳转）
  Future<void> openViewpointById(int viewpointId) async {
    logger.d('BooksController: 深链进入，构建种子+随机列表 ID=$viewpointId');
    try {
      // 切换到“所有书籍”随机模式，并设置深链种子
      await selectBook(-1);
      _mode = DisplayMode.deepLinkMix;
      _deepLinkSeedViewpointId = viewpointId;
      await loadAllViewpoints(); // 会把种子放在第一个，并滚到该页
    } catch (e, st) {
      logger.e('BooksController: 打开读书观点失败', error: e, stackTrace: st);
    }
  }

  /// 前往指定的观点索引，并更新状态
  void goToViewpointIndex(int index, {bool animate = false}) {
    currentViewpointIndex.value = index;
    if (!pageController.hasClients) {
      // 视图尚未挂载，延迟到下一帧尝试
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

  /// 手动刷新推荐列表（在“所有书籍”或“深链混合”模式下）
  Future<void> refreshRecommendations() async {
    if (filterBookID.value != -1) {
      // 指定书籍模式：按原逻辑重载
      await loadAllViewpoints();
      return;
    }
    // 重新执行当前模式的随机逻辑
    await loadAllViewpoints();
  }

  /// 内部：更新上次随机时间并安排自动刷新
  void _touchShuffleTimeAndSchedule() {
    _scheduleAutoRefresh();
  }

  void _scheduleAutoRefresh() {
    _cancelAutoRefreshIfAny();
    if (filterBookID.value != -1) return; // 仅在“所有/深链”模式下自动刷新

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

  /// 从全集中随机选取 n 条（不足则返回全部），顺序随机
  List<BookViewpointModel> _pickRandom(List<BookViewpointModel> all, int n) {
    final pool = List<BookViewpointModel>.from(all);
    pool.shuffle(_rand);
    if (pool.length <= n) return pool;
    return pool.take(n).toList();
  }

  /// 深链模式：指定种子观点 + 其余随机凑满 n 条
  List<BookViewpointModel> _buildDeepLinkMix(List<BookViewpointModel> all, int seedId) {
    final seed = all.firstWhereOrNull((v) => v.id == seedId);
    if (seed == null) {
      // 找不到种子则退回全量随机
      return _pickRandom(all, _kRandomCount);
    }
    final others = all.where((v) => v.id != seedId).toList();
    others.shuffle(_rand);
    final take = max(0, _kRandomCount - 1);
    final mixed = <BookViewpointModel>[seed, ...others.take(take)];
    return mixed;
  }
}

/// 展示模式
enum DisplayMode { allRandom, deepLinkMix, bookSpecific }
