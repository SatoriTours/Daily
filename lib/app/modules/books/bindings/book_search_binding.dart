import 'package:get/get.dart';
import 'package:daily_satori/app/modules/books/controllers/book_search_controller.dart';

/// 书籍搜索绑定
class BookSearchBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<BookSearchController>(() => BookSearchController())];
  }
}
