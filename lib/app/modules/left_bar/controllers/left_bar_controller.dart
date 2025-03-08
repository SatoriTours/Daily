import 'package:daily_satori/app_exports.dart';

class LeftBarController extends BaseController {
  ArticlesController get articlesController => Get.find<ArticlesController>();

  List<TagModel> get tags => TagsService.i.getAllTagModels();

  final isTagsExpanded = true.obs;
}
