import 'package:get/get.dart';

import '../controllers/left_bar_controller.dart';

class LeftBarBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<LeftBarController>(
      () => LeftBarController(),
    );
  }
}
