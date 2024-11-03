import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:get/get.dart';

class LeftBarController extends GetxController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  final List<String> tags = ['tag1', 'tag2', 'tag3'];
}
