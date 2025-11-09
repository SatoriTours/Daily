import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book_search_result.dart';

/// 书籍搜索控制器
class BookSearchController extends BaseController {
  final RxList<BookSearchResult> searchResults = <BookSearchResult>[].obs;
  final RxBool isLoading = false.obs;
  final TextEditingController searchController = TextEditingController();
  final RxString searchTerm = ''.obs;

  @override
  void onInit() {
    super.onInit();
    logger.i('BookSearchController initialized');
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
      isLoading.value = true;

      logger.i('选择添加书籍: ${bookResult.title}');

      final book = await BookService.i.addBookFromSearch(bookResult);

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
      logger.e('选择书籍失败', error: e, stackTrace: stackTrace);
      UIUtils.showError('添加书籍失败，请稍后重试');
    } finally {
      isLoading.value = false;
    }
  }

  /// 清空搜索结果
  void clearResults() {
    searchResults.clear();
    searchController.clear();
    searchTerm.value = '';
  }
}
