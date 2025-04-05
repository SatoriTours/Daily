import 'package:get/get.dart';
import 'package:daily_satori/app/modules/plugin_center/controllers/plugin_center_controller.dart';

/// 插件中心绑定类
class PluginCenterBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<PluginCenterController>(() => PluginCenterController())];
  }
}
