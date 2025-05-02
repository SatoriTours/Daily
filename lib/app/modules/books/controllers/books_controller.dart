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
  final addCategoryFormKey = GlobalKey<FormState>();

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
  final categoryNameController = TextEditingController();
  final categoryDescriptionController = TextEditingController();
  final feelingController = TextEditingController();

  @override
  void onInit() {
    super.onInit();
    loadBooks();
    loadAllViewpoints();
  }

  @override
  void onClose() {
    bookNameController.dispose();
    categoryNameController.dispose();
    categoryDescriptionController.dispose();
    feelingController.dispose();
    super.onClose();
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
          TextButton(onPressed: Get.back, child: const Text('取消')),
          Obx(
            () => TextButton(
              onPressed: isProcessing.value ? null : addBook,
              child: isProcessing.value ? const CircularProgressIndicator.adaptive() : const Text('添加'),
            ),
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
        bookNameController.clear();
        Get.back(); // 关闭对话框

        // 更新书籍列表并选择新添加的书籍
        await loadBooks();
        selectBook(book);

        Get.snackbar('添加成功', '书籍《${book.title}》已添加到书架');
      } else {
        Get.snackbar('添加失败', '无法添加书籍，请稍后重试');
      }

      isProcessing.value = false;
    } catch (e, stackTrace) {
      isProcessing.value = false;
      logger.e('添加书籍失败', error: e, stackTrace: stackTrace);
      Get.snackbar('添加失败', '发生错误，请稍后重试');
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

      final result = await _bookService.deleteBook(bookId);
      if (result) {
        // 更新本地数据
        books.removeWhere((b) => b.id == bookId);

        // 同时删除该书籍的所有观点
        allViewpoints.removeWhere((v) => v.bookId == bookId);

        // 如果删除的是当前选中的书籍，清空选择并重新加载所有观点
        if (selectedBook.value?.id == bookId) {
          selectedBook.value = null;
          loadAllViewpoints();
        }

        Get.snackbar('删除成功', '书籍《${book.title}》已删除');
      } else {
        Get.snackbar('删除失败', '无法删除书籍，请稍后重试');
      }

      isProcessing.value = false;
    } catch (e, stackTrace) {
      isProcessing.value = false;
      logger.e('删除书籍失败', error: e, stackTrace: stackTrace);
      Get.snackbar('删除失败', '发生错误，请稍后重试');
    }
  }

  /// 删除观点
  Future<void> deleteViewpoint(int viewpointId) async {
    try {
      isProcessing.value = true;

      final viewpointIndex = allViewpoints.indexWhere((v) => v.id == viewpointId);
      if (viewpointIndex < 0) {
        isProcessing.value = false;
        return;
      }

      final viewpoint = allViewpoints[viewpointIndex];
      final result = await _bookService.deleteViewpoint(viewpointId);

      if (result) {
        // 更新本地数据
        allViewpoints.removeAt(viewpointIndex);

        // 调整当前选中的观点索引
        if (allViewpoints.isEmpty) {
          currentViewpointIndex.value = 0;
          selectedBook.value = null;
        } else if (currentViewpointIndex.value >= allViewpoints.length) {
          currentViewpointIndex.value = allViewpoints.length - 1;
          updateSelectedBookFromViewpoint(allViewpoints[currentViewpointIndex.value]);
        }

        Get.snackbar('删除成功', '观点"${viewpoint.title}"已删除');
      } else {
        Get.snackbar('删除失败', '无法删除观点，请稍后重试');
      }

      isProcessing.value = false;
    } catch (e, stackTrace) {
      isProcessing.value = false;
      logger.e('删除观点失败', error: e, stackTrace: stackTrace);
      Get.snackbar('删除失败', '发生错误，请稍后重试');
    }
  }

  /// 显示添加分类对话框
  void showAddCategoryDialog() {
    categoryNameController.clear();
    categoryDescriptionController.clear();
    Get.dialog(
      AlertDialog(
        title: const Text('添加分类'),
        content: Form(
          key: addCategoryFormKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: categoryNameController,
                decoration: const InputDecoration(labelText: '分类名称', hintText: '请输入分类名称'),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return '请输入分类名称';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: categoryDescriptionController,
                decoration: const InputDecoration(labelText: '分类描述', hintText: '请输入分类描述（可选）'),
                maxLines: 3,
              ),
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: Get.back, child: const Text('取消')),
          Obx(
            () => TextButton(
              onPressed: isProcessing.value ? null : addCategory,
              child: isProcessing.value ? const CircularProgressIndicator.adaptive() : const Text('添加'),
            ),
          ),
        ],
      ),
    );
  }

  /// 添加分类
  Future<void> addCategory() async {
    if (addCategoryFormKey.currentState?.validate() != true) return;

    try {
      final categoryName = categoryNameController.text.trim();
      if (categoryName.isEmpty) return;

      isProcessing.value = true;
      final category = BookCategoryModel.create(
        name: categoryName,
        description: categoryDescriptionController.text.trim(),
      );

      final categoryId = await _bookService.saveCategory(category);
      if (categoryId > 0) {
        categoryNameController.clear();
        categoryDescriptionController.clear();
        Get.back(); // 关闭对话框

        Get.snackbar('添加成功', '分类"$categoryName"已添加');
      } else {
        Get.snackbar('添加失败', '无法添加分类，请稍后重试');
      }

      isProcessing.value = false;
    } catch (e, stackTrace) {
      isProcessing.value = false;
      logger.e('添加分类失败', error: e, stackTrace: stackTrace);
      Get.snackbar('添加失败', '发生错误，请稍后重试');
    }
  }

  /// 关闭抽屉
  void closeDrawer() {
    if (scaffoldKey.currentState?.isDrawerOpen == true) {
      scaffoldKey.currentState?.closeDrawer();
    }
  }

  /// 保存感悟
  Future<void> saveFeeling() async {
    try {
      final feeling = feelingController.text.trim();
      if (feeling.isEmpty) return;

      final currentViewpoint =
          currentViewpointIndex.value < allViewpoints.length ? allViewpoints[currentViewpointIndex.value] : null;

      if (currentViewpoint != null) {
        isProcessing.value = true;

        // 更新观点的感悟
        final updatedViewpoint = BookViewpointModel.create(
          id: currentViewpoint.id,
          bookId: currentViewpoint.bookId,
          title: currentViewpoint.title,
          content: currentViewpoint.content,
          example: currentViewpoint.example,
          feeling: feeling,
          createAt: currentViewpoint.createAt,
        );

        final result = await _bookService.saveViewpoint(updatedViewpoint);

        if (result > 0) {
          // 更新本地数据
          allViewpoints[currentViewpointIndex.value] = updatedViewpoint;
          feelingController.clear();
          Get.snackbar('保存成功', '感悟已保存');
        } else {
          Get.snackbar('保存失败', '无法保存感悟，请稍后重试');
        }

        isProcessing.value = false;
      }
    } catch (e, stackTrace) {
      isProcessing.value = false;
      logger.e('保存感悟失败', error: e, stackTrace: stackTrace);
      Get.snackbar('保存失败', '发生错误，请稍后重试');
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
}
