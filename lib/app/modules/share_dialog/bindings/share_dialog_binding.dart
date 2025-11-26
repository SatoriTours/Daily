import 'package:get/get.dart';

import '../controllers/share_dialog_controller.dart';
import '../../../services/state/article_state_service.dart';
import '../../../services/state/app_state_service.dart';

/// 分享对话框绑定
class ShareDialogBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [
      Bind.lazyPut<ShareDialogController>(() => ShareDialogController(
        Get.find<AppStateService>(),
        Get.find<ArticleStateService>(),
      )),
    ];
  }
}
