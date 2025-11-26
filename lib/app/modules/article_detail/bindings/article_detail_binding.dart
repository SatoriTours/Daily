import 'package:get/get.dart';

import '../controllers/article_detail_controller.dart';
import '../../../services/state/article_state_service.dart';
import '../../../services/state/app_state_service.dart';

/// 文章详情绑定
class ArticleDetailBinding extends Binding {
  @override
  List<Bind> dependencies() {
    // 使用 lazyPut 确保控制器只在需要时创建
    return [
      Bind.lazyPut<ArticleDetailController>(() => ArticleDetailController(
        Get.find<AppStateService>(),
        Get.find<ArticleStateService>(),
      )),
    ];
  }
}
