import 'package:get/get.dart';

import '../controllers/article_detail_controller.dart';

/// 文章详情绑定
class ArticleDetailBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<ArticleDetailController>(() => ArticleDetailController())];
  }
}
