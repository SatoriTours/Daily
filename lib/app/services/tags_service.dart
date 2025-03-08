import 'package:daily_satori/app_exports.dart';

class TagsService {
  // 单例模式
  TagsService._();
  static final TagsService _instance = TagsService._();
  static TagsService get i => _instance;

  // 只保存TagModel列表
  late List<TagModel> _tagModels;

  // 获取标签模型列表
  List<TagModel> get tagModels => _tagModels;

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
    // 直接通过仓库获取TagModel列表
    _tagModels = TagRepository.all();
    logger.i("[加载标签] 共加载 ${_tagModels.length} 个标签");
  }

  /// 清空所有标签
  Future<void> clearAllTags() async {
    // 使用仓库的方法删除所有标签
    TagRepository.removeAll();
    await _loadTags();
    logger.i("[清空标签] 已清空所有标签");
  }

  /// 添加标签到文章 - 调用仓库层方法
  Future<void> addTagToArticle(ArticleModel articleModel, String tagName) async {
    final result = await TagRepository.addTagToArticle(articleModel, tagName);

    if (result) {
      // 重新加载标签
      await reload();
    }
  }

  /// 获取TagModel列表
  List<TagModel> getAllTagModels() {
    return _tagModels;
  }

  /// 根据ID查找TagModel - 调用仓库层方法
  TagModel? findTagModelById(int id) {
    return TagRepository.findTagModelById(_tagModels, id);
  }

  /// 根据名称查找TagModel - 调用仓库层方法
  TagModel? findTagModelByName(String name) {
    return TagRepository.findTagModelByName(_tagModels, name);
  }
}
