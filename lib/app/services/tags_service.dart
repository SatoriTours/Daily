import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

class TagsService {
  TagsService._privateConstructor();
  static final TagsService _instance = TagsService._privateConstructor();
  static TagsService get i => _instance;

  late List<Tag> _tags;

  final tagBox = ObjectboxService.i.box<Tag>();

  List<Tag> get tags => _tags;

  Future<void> init() async {
    logger.i("[初始化服务] TagsService");
    await reload();
    // if (!isProduction) await clearAllTags();
  }

  Future<void> reload() async {
    _tags = tagBox.getAll();
    logger.i("[加载所有标签] ${_tags.length}");
  }

  Future<void> clearAllTags() async {
    tagBox.removeAll();
    await reload();
    logger.i("[清除所有标签] 完成");
  }
}
