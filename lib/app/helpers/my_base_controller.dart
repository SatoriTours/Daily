import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/global.dart';
import 'package:get/get.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

abstract class MyBaseController extends GetxController {
  late ISentrySpan _dbTr;

  @override
  void onInit() {
    logger.i("[onInit] ${Get.currentRoute} 数据库操作");
    _dbTr = DBService.i.startTransaction("Page:${Get.currentRoute}", '数据库操作');
    super.onInit();
  }

  @override
  void onClose() {
    DBService.i.stopTransaction(_dbTr);
    logger.i("[onClose] ${Get.currentRoute} 数据库操作");
    super.onClose();
  }
}
