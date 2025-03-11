import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app_exports.dart';

class LeftBarController extends BaseController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  List<TagModel> get tags => TagRepository.all();

  final isTagsExpanded = true.obs;
}
