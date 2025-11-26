import 'package:get/get.dart';

import '../controllers/diary_controller.dart';
import '../../../services/state/diary_state_service.dart';
import '../../../services/state/app_state_service.dart';

/// 日记绑定
class DiaryBinding extends Binding {
  @override
  List<Bind> dependencies() {
    // 使用 lazyPut 确保控制器只在需要时创建
    return [
      Bind.lazyPut<DiaryController>(() => DiaryController(
        Get.find<AppStateService>(),
        Get.find<DiaryStateService>(),
      )),
    ];
  }
}
