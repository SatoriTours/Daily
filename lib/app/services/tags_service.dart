import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';

class TagsService {
  // 单例模式
  TagsService._();
  static final TagsService _instance = TagsService._();
  static TagsService get i => _instance;

  // 标签数据
  late List<Tag> _tags;
  List<Tag> get tags => _tags;

  // ObjectBox 标签盒子
  final _tagBox = ObjectboxService.i.box<Tag>();

  /// 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] TagsService");
    await _loadTags();
  }

  /// 重新加载所有标签
  Future<void> reload() async {
    await _loadTags();
  }

  /// 加载标签数据
  Future<void> _loadTags() async {
    _tags = _tagBox.getAll();
    logger.i("[加载标签] 共加载 ${_tags.length} 个标签");
  }

  /// 清空所有标签
  Future<void> clearAllTags() async {
    _tagBox.removeAll();
    await _loadTags();
    logger.i("[清空标签] 已清空所有标签");
  }
}
