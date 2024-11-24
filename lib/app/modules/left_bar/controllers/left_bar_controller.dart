import 'package:daily_satori/app/helpers/my_base_controller.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:get/get.dart';

class LeftBarController extends MyBaseController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  List<Tag> get tags => TagsService.i.tags;
}
