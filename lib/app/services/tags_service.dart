import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/global.dart';
import 'package:drift/drift.dart';

class TagsService {
  TagsService._privateConstructor();
  static final TagsService _instance = TagsService._privateConstructor();
  static TagsService get i => _instance;

  final _db = DBService.i.db;
  late List<Tag> _tags;

  List<Tag> get tags => _tags;

  Future<void> init() async {
    logger.i("[初始化服务] TagsService");
    await reload();
    // if (!isProduction) await clearAllTags();
  }

  Future<void> reload() async {
    _tags = await _db.tags.select().get();
    logger.i("[加载所有标签] ${_tags.length}");
  }

  List<String> getStringTags() {
    return _tags.map((tag) => tag.title).where((title) => title != null).map((title) => title!).toList();
  }

  Future<void> clearAllTags() async {
    await _db.articleTags.deleteAll();
    await _db.tags.deleteAll();
    logger.i("[清除所有标签] 完成");
  }
}
