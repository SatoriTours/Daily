import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/books/controllers/books_controller.dart';

class BooksBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<BooksController>(() => BooksController())];
  }
}
