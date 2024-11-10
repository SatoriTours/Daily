import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:get/get.dart';

class LeftBarController extends GetxController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  final tags = <Tag>[].obs;

  @override
  void onInit() {
    super.onInit();
    tags.addAll(TagsService.i.tags);
  }
}
