import 'package:get/get.dart';

import '../controllers/ai_config_edit_controller.dart';

/// AI配置编辑页面绑定
class AIConfigEditBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<AIConfigEditController>(() => AIConfigEditController())];
  }
}
