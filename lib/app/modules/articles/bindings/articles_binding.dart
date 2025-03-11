import 'package:get/get.dart';

import '../controllers/articles_controller.dart';

class ArticlesBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<ArticlesController>(() => ArticlesController())];
  }
}
