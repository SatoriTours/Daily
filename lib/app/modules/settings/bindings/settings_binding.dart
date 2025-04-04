import 'package:get/get.dart';

import '../controllers/settings_controller.dart';

/// 设置页面绑定
class SettingsBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<SettingsController>(() => SettingsController())];
  }
}
