import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/services/book_service.dart';

/// 读书页面控制器
///
/// 负责管理读书页面的状态和交互逻辑
class BooksController extends BaseController {
  final BookService _bookService = BookService.i;
  final scaffoldKey = GlobalKey<ScaffoldState>();
  final addBookFormKey = GlobalKey<FormState>();
  final addCategoryFormKey = GlobalKey<FormState>();

  // 书籍和分类数据
  final books = <BookModel>[].obs;
  final categories = <BookCategoryModel>[].obs;
  final selectedCategoryIndex = RxInt(-1); // -1表示全部书籍

  // 当前选中的书籍和观点
  final selectedBook = Rx<BookModel?>(null);
  final bookViewpoints = <BookViewpointModel>[].obs;
  final currentViewpointIndex = 0.obs;

  // 加载状态
  final isLoadingBooks = false.obs;
  final isLoadingViewpoints = false.obs;
  final isProcessing = false.obs;

  // 表单控制器
  final bookNameController = TextEditingController();
  final categoryNameController = TextEditingController();
  final feelingController = TextEditingController();

  BooksController();

  @override
  void onInit() {
    super.onInit();
    loadBooksAndCategories();
  }

  @override
  void onClose() {
    bookNameController.dispose();
    categoryNameController.dispose();
    feelingController.dispose();
    super.onClose();
  }

  /// 加载书籍和分类数据
  Future<void> loadBooksAndCategories() async {
    try {
      isLoadingBooks.value = true;

      // 获取所有书籍和分类
      books.value = await _bookService.getBooks();
      categories.value = await _bookService.getCategories();

      if (selectedBook.value == null && books.isNotEmpty) {
        // 默认选中第一本书
        selectBook(books.first);
      }

      isLoadingBooks.value = false;
    } catch (e, stackTrace) {
      isLoadingBooks.value = false;
      logger.e('加载书籍和分类失败', error: e, stackTrace: stackTrace);
      Get.snackbar('加载失败', '无法加载书籍数据，请稍后重试');
    }
  }

  /// 选择书籍分类
  void selectCategory(int index) {
    selectedCategoryIndex.value = index;
    // 如果是全部类别，不筛选
    if (index == -1) {
      loadBooksAndCategories();
      return;
    }

    // 筛选当前分类的书籍
    final category = categories[index];
    filterBooksByCategory(category.name);
  }

  /// 根据分类过滤书籍
  Future<void> filterBooksByCategory(String category) async {
    try {
      isLoadingBooks.value = true;
      books.value = await _bookService.getBooksByCategory(category);
      isLoadingBooks.value = false;

      // 切换分类后，如果有书籍，选择第一本
      if (books.isNotEmpty) {
        selectBook(books.first);
      } else {
        selectedBook.value = null;
        bookViewpoints.clear();
      }
    } catch (e, stackTrace) {
      isLoadingBooks.value = false;
      logger.e('筛选书籍失败: $category', error: e, stackTrace: stackTrace);
    }
  }

  /// 选择书籍
  Future<void> selectBook(BookModel book) async {
    try {
      selectedBook.value = book;
      bookViewpoints.clear();
      currentViewpointIndex.value = 0;

      // 加载书籍观点
      isLoadingViewpoints.value = true;
      final viewpoints = await _bookService.getBookViewpoints(book);
      bookViewpoints.value = viewpoints;
      isLoadingViewpoints.value = false;

      // 清空感悟输入
      feelingController.clear();
    } catch (e, stackTrace) {
      isLoadingViewpoints.value = false;
      logger.e('选择书籍失败: ${book.title}', error: e, stackTrace: stackTrace);
    }
  }

  /// 添加书籍
  Future<void> addBook() async {
    if (addBookFormKey.currentState?.validate() != true) return;

    try {
      final bookName = bookNameController.text.trim();
      if (bookName.isEmpty) return;

      isProcessing.value = true;
      final selectedCategory = selectedCategoryIndex.value >= 0 ? categories[selectedCategoryIndex.value].name : '';

      final book = await _bookService.addBook(bookName, category: selectedCategory);
      if (book != null) {
        bookNameController.clear();
        Get.back(); // 关闭对话框

        // 更新书籍列表并选择新添加的书籍
        await loadBooksAndCategories();
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

  /// 添加分类
  Future<void> addCategory() async {
    if (addCategoryFormKey.currentState?.validate() != true) return;

    try {
      final categoryName = categoryNameController.text.trim();
      if (categoryName.isEmpty) return;

      isProcessing.value = true;

      // 检查分类是否已存在
      final existingCategory = categories.firstWhereOrNull((c) => c.name == categoryName);
      if (existingCategory != null) {
        Get.snackbar('添加失败', '分类"$categoryName"已存在');
        isProcessing.value = false;
        return;
      }

      // 保存分类
      final category = BookCategoryModel.create(name: categoryName);
      final categoryId = await _bookService.saveCategory(category);

      if (categoryId > 0) {
        categoryNameController.clear();
        Get.back(); // 关闭对话框

        // 获取推荐书籍
        final recommendedBooks = await _bookService.getRecommendedBooksByCategory(categoryName);

        // 更新分类列表
        await loadBooksAndCategories();

        // 设置当前分类为新添加的分类
        final newCategoryIndex = categories.indexWhere((c) => c.name == categoryName);
        if (newCategoryIndex >= 0) {
          selectCategory(newCategoryIndex);
        }

        final message =
            recommendedBooks.isEmpty
                ? '分类"$categoryName"已添加'
                : '分类"$categoryName"已添加，并自动添加了${recommendedBooks.length}本相关书籍';

        Get.snackbar('添加成功', message);
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

  /// 切换到下一个观点
  void nextViewpoint() {
    if (bookViewpoints.isEmpty) return;

    if (currentViewpointIndex.value < bookViewpoints.length - 1) {
      currentViewpointIndex.value++;
      feelingController.text = bookViewpoints[currentViewpointIndex.value].feeling;
    }
  }

  /// 切换到上一个观点
  void previousViewpoint() {
    if (bookViewpoints.isEmpty) return;

    if (currentViewpointIndex.value > 0) {
      currentViewpointIndex.value--;
      feelingController.text = bookViewpoints[currentViewpointIndex.value].feeling;
    }
  }

  /// 保存当前观点的感悟
  Future<void> saveFeeling() async {
    if (bookViewpoints.isEmpty || currentViewpointIndex.value >= bookViewpoints.length) return;

    try {
      final feeling = feelingController.text.trim();
      final viewpoint = bookViewpoints[currentViewpointIndex.value];

      if (feeling == viewpoint.feeling) return; // 如果感悟没变，不保存

      final result = await _bookService.updateViewpointFeeling(viewpoint.id, feeling);
      if (result) {
        // 更新本地数据
        viewpoint.feeling = feeling;
        bookViewpoints[currentViewpointIndex.value] = viewpoint;

        Get.snackbar('保存成功', '感悟已保存');
      }
    } catch (e, stackTrace) {
      logger.e('保存感悟失败', error: e, stackTrace: stackTrace);
      Get.snackbar('保存失败', '无法保存感悟，请稍后重试');
    }
  }

  /// 打开左侧抽屉栏
  void openDrawer() {
    scaffoldKey.currentState?.openDrawer();
  }

  /// 关闭抽屉栏
  void closeDrawer() {
    scaffoldKey.currentState?.closeDrawer();
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

  /// 显示添加分类对话框
  void showAddCategoryDialog() {
    categoryNameController.clear();
    Get.dialog(
      AlertDialog(
        title: const Text('添加分类'),
        content: Form(
          key: addCategoryFormKey,
          child: TextFormField(
            controller: categoryNameController,
            decoration: const InputDecoration(labelText: '分类名称', hintText: '请输入分类名称'),
            validator: (value) {
              if (value == null || value.trim().isEmpty) {
                return '请输入分类名称';
              }
              return null;
            },
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

        // 如果删除的是当前选中的书籍，选择另一本书或清空选择
        if (selectedBook.value?.id == bookId) {
          if (books.isNotEmpty) {
            selectBook(books.first);
          } else {
            selectedBook.value = null;
            bookViewpoints.clear();
          }
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

      final viewpointIndex = bookViewpoints.indexWhere((v) => v.id == viewpointId);
      if (viewpointIndex < 0) {
        isProcessing.value = false;
        return;
      }

      final viewpoint = bookViewpoints[viewpointIndex];
      final result = await _bookService.deleteViewpoint(viewpointId);

      if (result) {
        // 更新本地数据
        bookViewpoints.removeAt(viewpointIndex);

        // 调整当前选中的观点索引
        if (bookViewpoints.isEmpty) {
          currentViewpointIndex.value = 0;
        } else if (currentViewpointIndex.value >= bookViewpoints.length) {
          currentViewpointIndex.value = bookViewpoints.length - 1;
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
}
