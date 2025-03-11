import 'package:get/get.dart';

import '../controllers/article_detail_controller.dart';

class ArticleDetailBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<ArticleDetailController>(() => ArticleDetailController())];
  }
}
