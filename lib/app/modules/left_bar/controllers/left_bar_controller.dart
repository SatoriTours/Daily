import 'package:get/get.dart';
import 'package:daily_satori/app/utils/base_controller.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/tags_service.dart';

class LeftBarController extends BaseController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  List<Tag> get tags => TagsService.i.tags;

  final isTagsExpanded = true.obs;

  @override
  void onInit() {
    super.onInit();
  }

  @override
  void onReady() {
    super.onReady();
  }

  @override
  void onClose() {
    super.onClose();
  }
}
