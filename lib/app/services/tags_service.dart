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

  Future<void> init() async {
    logger.i("[初始化服务] TagsService");
    await _loadAllTags();
  }

  Future<void> _loadAllTags() async {
    _tags = await _db.tags.select().get();
    logger.i("[加载所有标签] ${_tags.length}");
  }

  String getTagsString() {
    return _tags.map((tag) => tag.title).join(',');
  }
}
