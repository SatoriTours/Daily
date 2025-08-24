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

  @override
  void onInit() {
    super.onInit();
    loadAllViewpoints();
  }

  @override
  void onClose() {
    bookNameController.dispose();
    scrollController.dispose();
    super.onClose();
  }

  /// 加载所有书籍
  List<BookModel> getAllBooks() {
    return _bookService.getBooks();
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    // 获取所有观点
    logger.i('加载观点: bookID: ${filterBookID.value}');
    if (filterBookID.value == -1) {
      allViewpoints.value = await BookRepository.getAllViewpointsAsync();
    } else {
      allViewpoints.value = await BookRepository.getViewpointsByBookIdsAsync([filterBookID.value]);
    }

    // 如果有观点，选择第一个
    if (allViewpoints.isNotEmpty) {
      currentViewpointIndex.value = 0;
    }
  }

  /// 选择书籍
  Future<void> selectBook(int bookID) async {
    filterBookID.value = bookID;
  }

  BookViewpointModel currentViewpoint() {
    return allViewpoints[currentViewpointIndex.value];
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
}
