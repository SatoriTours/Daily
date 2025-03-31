import 'package:get/get.dart';

import '../controllers/ai_config_controller.dart';

/// AI配置绑定
class AIConfigBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<AIConfigController>(() => AIConfigController());
  }
}
