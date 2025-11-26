import 'package:daily_satori/app_exports.dart';

/// 书籍搜索控制器
class BookSearchController extends BaseController {
  final RxList<BookSearchResult> searchResults = <BookSearchResult>[].obs;
  final TextEditingController searchController = TextEditingController();
  final RxString searchTerm = ''.obs;

  @override
  void onInit() {
    super.onInit();
    logger.i('BookSearchController initialized');

    // 如果有传入的搜索关键词，自动搜索
    final String? initialSearchTerm = Get.arguments as String?;
    if (initialSearchTerm != null && initialSearchTerm.isNotEmpty) {
      searchController.text = initialSearchTerm;
      searchBooks(initialSearchTerm);
    }
  }

  @override
  void onClose() {
    searchController.dispose();
    super.onClose();
    logger.i('BookSearchController disposed');
  }

  /// 搜索书籍
  Future<void> searchBooks(String term) async {
    if (term.trim().isEmpty) {
      UIUtils.showError('请输入书名');
      return;
    }

    try {
      isLoading.value = true;
      searchTerm.value = term.trim();

      logger.i('开始搜索书籍: ${searchTerm.value}');

      final results = await BookService.i.searchBooks(searchTerm.value);

      searchResults.assignAll(results);
      logger.i('搜索完成，找到 ${results.length} 个结果');
    } catch (e, stackTrace) {
      logger.e('搜索书籍失败', error: e, stackTrace: stackTrace);
      UIUtils.showError('搜索失败，请稍后重试');
    } finally {
      isLoading.value = false;
    }
  }

  /// 选择书籍
  Future<void> selectBook(BookSearchResult bookResult) async {
    try {
      // 显示加载提示
      DialogUtils.showLoading(tips: '正在添加《${bookResult.title}》...');

      logger.i('选择添加书籍: ${bookResult.title}');

      final book = await BookService.i.addBookFromSearch(bookResult);

      // 隐藏加载提示
      DialogUtils.hideLoading();

      Get.back(); // 关闭搜索页面

      if (book != null) {
        UIUtils.showSuccess('《${book.title}》已添加到书架');

        // 通知父页面刷新
        if (Get.isRegistered<BooksStateService>()) {
          final booksService = Get.find<BooksStateService>();
          await booksService.loadAllViewpoints();
        }
      } else {
        UIUtils.showError('添加书籍失败，可能该书籍已存在');
      }
    } catch (e, stackTrace) {
      // 确保隐藏加载提示
      DialogUtils.hideLoading();
      logger.e('选择书籍失败', error: e, stackTrace: stackTrace);
      UIUtils.showError('添加书籍失败，请稍后重试');
    }
  }

  /// 清空搜索结果
  void clearResults() {
    searchResults.clear();
    searchController.clear();
    searchTerm.value = '';
  }
}
