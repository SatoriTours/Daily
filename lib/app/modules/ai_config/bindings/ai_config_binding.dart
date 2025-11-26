import 'package:get/get.dart';

import '../controllers/ai_config_controller.dart';
import '../../../services/state/ai_config_state_service.dart';
import '../../../services/state/app_state_service.dart';

/// AI配置绑定
class AIConfigBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [
      Bind.lazyPut<AIConfigController>(() => AIConfigController(
        Get.find<AppStateService>(),
        Get.find<AIConfigStateService>(),
      )),
    ];
  }
}
