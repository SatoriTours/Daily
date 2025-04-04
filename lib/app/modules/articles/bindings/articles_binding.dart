import 'package:get/get.dart';

import '../controllers/articles_controller.dart';

/// 文章列表绑定
class ArticlesBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<ArticlesController>(() => ArticlesController())];
  }
}
