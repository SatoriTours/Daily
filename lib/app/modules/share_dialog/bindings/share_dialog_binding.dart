import 'package:get/get.dart';

import '../controllers/share_dialog_controller.dart';

class ShareDialogBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<ShareDialogController>(
      () => ShareDialogController(),
    );
  }
}
