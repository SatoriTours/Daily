import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/repositories/book_repository.dart';

/// 读书页面控制器
///
/// 负责管理读书页面的状态和交互逻辑
class BooksController extends BaseController {
  final BookService _bookService = BookService.i;
  final scaffoldKey = GlobalKey<ScaffoldState>();
  final addBookFormKey = GlobalKey<FormState>();

  // 书籍数据
  final books = <BookModel>[].obs;

  // 所有观点和当前观点
  final allViewpoints = <BookViewpointModel>[].obs;
  final currentViewpointIndex = 0.obs;

  // 当前选中的书籍
  final selectedBook = Rx<BookModel?>(null);

  // 加载状态
  final isLoadingBooks = false.obs;
  final isLoadingViewpoints = false.obs;
  final isProcessing = false.obs;

  // 表单控制器
  final bookNameController = TextEditingController();

  // 滚动控制器
  final scrollController = ScrollController();

  // 分页大小
  final int _pageSize = 20;

  @override
  void onInit() {
    super.onInit();
    _initScrollListener();
    loadBooks();
    loadAllViewpoints();
  }

  @override
  void onClose() {
    bookNameController.dispose();
    scrollController.dispose();
    super.onClose();
  }

  /// 初始化滚动监听器
  void _initScrollListener() {
    scrollController.addListener(() {
      if (!scrollController.hasClients) return;

      final position = scrollController.position;
      if (position.pixels == position.maxScrollExtent) {
        _loadMoreViewpoints();
      } else if (position.pixels == position.minScrollExtent) {
        _loadPreviousViewpoints();
      }
    });
  }

  /// 加载所有书籍
  Future<void> loadBooks() async {
    try {
      isLoadingBooks.value = true;
      books.value = await _bookService.getBooks();
      isLoadingBooks.value = false;
    } catch (e, stackTrace) {
      isLoadingBooks.value = false;
      logger.e('加载书籍失败', error: e, stackTrace: stackTrace);
      Get.snackbar('加载失败', '无法加载书籍数据，请稍后重试');
    }
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    try {
      isLoadingViewpoints.value = true;

      // 获取所有观点
      final viewpoints = await BookRepository.getAllViewpoints();
      allViewpoints.value = viewpoints;

      // 如果有观点，选择第一个
      if (allViewpoints.isNotEmpty) {
        currentViewpointIndex.value = 0;
        updateSelectedBookFromViewpoint(allViewpoints.first);
      }

      isLoadingViewpoints.value = false;
    } catch (e, stackTrace) {
      isLoadingViewpoints.value = false;
      logger.e('加载观点失败', error: e, stackTrace: stackTrace);
      Get.snackbar('加载失败', '无法加载观点数据，请稍后重试');
    }
  }

  /// 根据观点更新当前选中的书籍
  void updateSelectedBookFromViewpoint(BookViewpointModel viewpoint) {
    final bookId = viewpoint.bookId;
    final book = books.firstWhereOrNull((b) => b.id == bookId);
    selectedBook.value = book;
  }

  /// 选择书籍
  Future<void> selectBook(BookModel book) async {
    try {
      selectedBook.value = book;

      // 筛选该书的所有观点
      isLoadingViewpoints.value = true;
      final viewpoints = await BookRepository.getViewpoints(book.id);
      allViewpoints.value = viewpoints;
      isLoadingViewpoints.value = false;

      // 重置观点索引
      if (allViewpoints.isNotEmpty) {
        currentViewpointIndex.value = 0;
      }

      // 滚动到顶部
      if (scrollController.hasClients) {
        scrollController.jumpTo(0);
      }
    } catch (e, stackTrace) {
      isLoadingViewpoints.value = false;
      logger.e('选择书籍失败: ${book.title}', error: e, stackTrace: stackTrace);
      Get.snackbar('加载失败', '无法加载书籍观点，请稍后重试');
    }
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
    try {
      isProcessing.value = true;

      final book = books.firstWhereOrNull((b) => b.id == bookId);
      if (book == null) {
        isProcessing.value = false;
        return;
      }

      await _bookService.deleteBook(bookId);

      await loadAllViewpoints();

      UIUtils.showSuccess('书籍《${book.title}》已删除');

      isProcessing.value = false;
    } catch (e, stackTrace) {
      isProcessing.value = false;
      logger.e('删除书籍失败', error: e, stackTrace: stackTrace);
      UIUtils.showError('发生错误，请稍后重试');
    }
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

  /// 加载更多观点
  Future<void> _loadMoreViewpoints() async {
    if (allViewpoints.isEmpty || selectedBook.value == null) return;

    isLoadingViewpoints.value = true;

    try {
      final lastId = allViewpoints.last.id;
      logger.i('加载ID:$lastId之后的观点');

      // 这里需要实现从BookRepository获取更多观点的逻辑
      // 示例实现，根据实际情况可能需要修改
      final bookId = selectedBook.value!.id;
      final moreViewpoints = await _fetchViewpoints(bookId, lastId, false);

      if (moreViewpoints.isNotEmpty) {
        allViewpoints.addAll(moreViewpoints);
      }
    } finally {
      isLoadingViewpoints.value = false;
    }
  }

  /// 加载之前的观点
  Future<void> _loadPreviousViewpoints() async {
    if (allViewpoints.isEmpty || selectedBook.value == null) return;

    isLoadingViewpoints.value = true;

    try {
      final firstId = allViewpoints.first.id;
      logger.i('加载ID:$firstId之前的观点');

      // 这里需要实现从BookRepository获取之前观点的逻辑
      // 示例实现，根据实际情况可能需要修改
      final bookId = selectedBook.value!.id;
      final previousViewpoints = await _fetchViewpoints(bookId, firstId, true);

      if (previousViewpoints.isNotEmpty) {
        allViewpoints.insertAll(0, previousViewpoints);
      }
    } finally {
      isLoadingViewpoints.value = false;
    }
  }

  /// 获取特定范围的观点
  Future<List<BookViewpointModel>> _fetchViewpoints(int bookId, int referenceId, bool isGreaterThan) async {
    try {
      // 这里需要扩展BookRepository的功能来支持分页加载
      // 现在仅为示例，实际实现可能需要调整
      final allBookViewpoints = await BookRepository.getViewpoints(bookId);

      if (isGreaterThan) {
        // 获取ID大于referenceId的观点（较新的）
        return allBookViewpoints.where((v) => v.id > referenceId).take(_pageSize).toList();
      } else {
        // 获取ID小于referenceId的观点（较旧的）
        return allBookViewpoints.where((v) => v.id < referenceId).take(_pageSize).toList();
      }
    } catch (e, stackTrace) {
      logger.e('获取观点失败', error: e, stackTrace: stackTrace);
      return [];
    }
  }
}
