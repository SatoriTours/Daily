import 'package:get/get.dart';
import 'package:daily_satori/app/services/state/app_state_service.dart';

import '../controllers/settings_controller.dart';

/// 设置页面绑定
class SettingsBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<SettingsController>(() => SettingsController(Get.find<AppStateService>()))];
  }
}
