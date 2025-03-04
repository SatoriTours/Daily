import 'package:get/get.dart';

/// 基础控制器
/// 所有控制器的基类，提供通用功能
abstract class BaseController extends GetxController {
  // late ISentrySpan _dbTr;

  @override
  void onInit() {
    // logger.i("[onInit] ${Get.currentRoute} 数据库操作");
    // _dbTr = DBService.i.startTransaction("Page:${Get.currentRoute}", '数据库操作');
    super.onInit();
  }

  @override
  void onClose() {
    // DBService.i.stopTransaction(_dbTr);
    // logger.i("[onClose] ${Get.currentRoute} 数据库操作");
    super.onClose();
  }
}
