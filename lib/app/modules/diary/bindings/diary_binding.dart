import 'package:get/get.dart';

import '../controllers/diary_controller.dart';

class DiaryBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<DiaryController>(() => DiaryController());
  }
}
