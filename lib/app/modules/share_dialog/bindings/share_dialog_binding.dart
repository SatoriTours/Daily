import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// ShareDialog模块的绑定类
/// 负责注册控制器和服务依赖
class ShareDialogBinding extends Bindings {
  @override
  void dependencies() {
    // 注册控制器
    Get.lazyPut<ShareDialogController>(() => ShareDialogController());
  }
}
