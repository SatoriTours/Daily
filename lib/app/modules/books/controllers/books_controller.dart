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
  final addBookFormKey = GlobalKey<FormState>();

  // 所有观点和当前观点
  final allViewpoints = <BookViewpointModel>[].obs;
  final currentViewpointIndex = 0.obs;

  // 当前选中的书籍
  final filterBookID = (-1).obs;

  final isProcessing = false.obs;

  // 表单控制器
  final bookNameController = TextEditingController();

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
    bookNameController.dispose();
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
      // 所有书籍：根据模式选择随机数据
      final all = await BookRepository.getAllViewpointsAsync();
      if (all.isEmpty) {
        allViewpoints.clear();
        return;
      }
      if (_mode == DisplayMode.deepLinkMix && _deepLinkSeedViewpointId != null) {
        allViewpoints.value = _buildDeepLinkMix(all, _deepLinkSeedViewpointId!);
        goToViewpointIndex(0);
      } else {
        _mode = DisplayMode.allRandom; // 兜底为全量随机
        allViewpoints.value = _pickRandom(all, _kRandomCount);
        goToViewpointIndex(0);
      }
      _touchShuffleTimeAndSchedule();
    } else {
      // 某本书：展示该书全部
      _mode = DisplayMode.bookSpecific;
      allViewpoints.value = await BookRepository.getViewpointsByBookIdsAsync([filterBookID.value]);

      // 如果该书没有观点，尝试重新获取
      if (allViewpoints.isEmpty) {
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

      if (allViewpoints.isNotEmpty) {
        goToViewpointIndex(0);
      }
      _cancelAutoRefreshIfAny(); // 书内全部不参与12小时自动刷新
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
  void showAddBookDialog() {
    bookNameController.clear();
    Get.dialog(
      AlertDialog(
        title: const Text('添加书籍'),
        content: Form(
          key: addBookFormKey,
          child: TextFormField(
            controller: bookNameController,
            decoration: const InputDecoration(labelText: '书名', hintText: '请输入书名'),
            validator: (value) {
              if (value == null || value.trim().isEmpty) {
                return '请输入书名';
              }
              return null;
            },
          ),
        ),
        actions: [
          TextButton(onPressed: Get.close, child: const Text('取消')),
          TextButton(
            onPressed: () async {
              if (addBookFormKey.currentState?.validate() != true) return;
              Get.close();
              isProcessing.value = true;
              await addBook();
              isProcessing.value = false;
            },
            child: const Text('添加'),
          ),
        ],
      ),
    );
  }

  /// 添加书籍
  Future<void> addBook() async {
    if (addBookFormKey.currentState?.validate() != true) return;

    try {
      final bookName = bookNameController.text.trim();
      if (bookName.isEmpty) return;

      isProcessing.value = true;
      final book = await _bookService.addBook(bookName);

      if (book != null) {
        // 更新书籍列表并选择新添加的书籍
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
