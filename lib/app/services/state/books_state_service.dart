import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/models/book_viewpoint.dart';
import 'package:daily_satori/app/repositories/book_viewpoint_repository.dart';
import 'dart:math';

/// 读书模块全局状态管理服务
///
/// 职责：
/// 1. 管理书籍列表和观点列表数据（唯一数据源）
/// 2. 处理书籍和观点的加载、更新、删除操作
/// 3. 管理当前活跃观点和筛选状态
class BooksStateService extends GetxService {
  // ===== 数据层（唯一数据源） =====

  /// 所有观点列表
  final RxList<BookViewpointModel> viewpoints = <BookViewpointModel>[].obs;

  /// 加载状态
  final RxBool isLoading = false.obs;

  /// 当前观点索引
  final RxInt currentViewpointIndex = 0.obs;

  /// 当前选中的书籍ID（-1表示全部）
  final RxInt filterBookID = (-1).obs;

  /// 处理中状态
  final RxBool isProcessing = false.obs;

  // ===== 内部状态 =====

  /// 展示模式
  DisplayMode _mode = DisplayMode.allRandom;

  /// 深链模式下需要优先展示的观点ID
  int? _deepLinkSeedViewpointId;

  /// 随机数生成器
  final Random _rand = Random();

  /// 随机推荐的观点数量
  static const int _kRandomCount = 10;

  // ===== Getters =====

  /// 获取当前观点
  BookViewpointModel? get currentViewpoint {
    if (viewpoints.isEmpty) return null;
    final index = currentViewpointIndex.value.clamp(0, viewpoints.length - 1);
    return viewpoints[index];
  }

  /// 获取所有书籍
  List<BookModel> get allBooks {
    return BookService.i.getBooks();
  }

  // ===== 数据操作方法 =====

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    logger.i('加载观点: bookID: ${filterBookID.value}, mode: $_mode');

    isLoading.value = true;
    try {
      if (filterBookID.value == -1) {
        await _loadAllBooksViewpoints();
      } else {
        await _loadSpecificBookViewpoints();
      }
    } finally {
      isLoading.value = false;
    }
  }

  /// 加载所有书籍的观点（随机模式）
  Future<void> _loadAllBooksViewpoints() async {
    final allViewpoints = BookViewpointRepository.i.all();
    if (allViewpoints.isEmpty) {
      viewpoints.clear();
      return;
    }

    _setRandomModeViewpoints(allViewpoints);
  }

  /// 根据模式设置随机观点
  void _setRandomModeViewpoints(List<BookViewpointModel> allViewpoints) {
    if (_mode == DisplayMode.deepLinkMix && _deepLinkSeedViewpointId != null) {
      viewpoints.value = _buildDeepLinkMix(allViewpoints, _deepLinkSeedViewpointId!);
    } else {
      _mode = DisplayMode.allRandom; // 兜底为全量随机
      viewpoints.value = _pickRandom(allViewpoints, _kRandomCount);
    }
  }

  /// 加载特定书籍的观点
  Future<void> _loadSpecificBookViewpoints() async {
    _mode = DisplayMode.bookSpecific;
    viewpoints.value = BookViewpointRepository.i.findModelsByBookIds([filterBookID.value]);

    if (viewpoints.isEmpty) {
      await _tryRefreshBookViewpoints();
    }

    if (viewpoints.isNotEmpty) {
      currentViewpointIndex.value = 0;
    }
  }

  /// 尝试重新获取书籍观点
  Future<void> _tryRefreshBookViewpoints() async {
    logger.i('书籍 ${filterBookID.value} 没有观点，尝试重新获取...');
    try {
      final success = await BookService.i.refreshBook(filterBookID.value);
      if (success) {
        viewpoints.value = BookViewpointRepository.i.findModelsByBookIds([filterBookID.value]);
        logger.i('重新获取书籍观点成功，共 ${viewpoints.length} 条');
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

  /// 添加书籍
  Future<BookModel?> addBook(String bookName) async {
    isProcessing.value = true;
    try {
      return await BookService.i.addBook(bookName);
    } finally {
      isProcessing.value = false;
    }
  }

  /// 删除书籍
  Future<void> deleteBook(int bookId) async {
    await BookService.i.deleteBook(bookId);
    await loadAllViewpoints();
  }

  /// 刷新书籍内容
  Future<bool> refreshBook(int bookId) async {
    try {
      return await BookService.i.refreshBook(bookId);
    } catch (_) {
      return false;
    }
  }

  /// 通过观点ID打开并定位（用于深链跳转）
  Future<void> openViewpointById(int viewpointId) async {
    logger.d('BooksStateService: 深链进入，构建种子+随机列表 ID=$viewpointId');
    try {
      // 切换到"所有书籍"随机模式，并设置深链种子
      await selectBook(-1);
      _mode = DisplayMode.deepLinkMix;
      _deepLinkSeedViewpointId = viewpointId;
      await loadAllViewpoints();
    } catch (e, st) {
      logger.e('BooksStateService: 打开读书观点失败', error: e, stackTrace: st);
    }
  }

  /// 前往指定的观点索引
  void goToViewpointIndex(int index) {
    currentViewpointIndex.value = index.clamp(0, viewpoints.length - 1);
  }

  /// 手动刷新推荐列表
  Future<void> refreshRecommendations() async {
    await loadAllViewpoints();
  }

  // ===== 私有辅助方法 =====

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

  @override
  void onInit() {
    super.onInit();
    logger.i('BooksStateService 初始化完成');
  }

  @override
  void onClose() {
    viewpoints.close();
    isLoading.close();
    currentViewpointIndex.close();
    filterBookID.close();
    isProcessing.close();
    super.onClose();
    logger.i('BooksStateService 已关闭');
  }
}

/// 展示模式
enum DisplayMode { allRandom, deepLinkMix, bookSpecific }
