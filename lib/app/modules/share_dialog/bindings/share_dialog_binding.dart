import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// ShareDialog模块的绑定类
/// 负责注册控制器和服务依赖
class ShareDialogBinding extends Binding {
  @override
  List<Bind> dependencies() {
    // 注册控制器
    return [Bind.lazyPut<ShareDialogController>(() => ShareDialogController())];
  }
}
