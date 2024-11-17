import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:daily_satori/global.dart';
import 'package:get/get.dart';

class LeftBarController extends MyBaseController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  final tags = <Tag>[].obs;

  void refreshData() {
    logger.i("[LeftBarController] 刷新标签");
    tags.assignAll(TagsService.i.tags);
  }
}
