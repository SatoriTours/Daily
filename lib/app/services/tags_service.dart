import 'package:daily_satori/app_exports.dart';

/// 标签服务类
///
/// 提供标签相关的业务功能，管理标签数据的访问和操作
class TagsService {
  // MARK: - 单例实现
  TagsService._();
  static final TagsService _instance = TagsService._();
  static TagsService get i => _instance;

  // MARK: - 属性

  /// 缓存的标签模型列表
  late List<TagModel> _tagModels;

  /// 获取所有标签模型
  List<TagModel> get tagModels => _tagModels;

  // MARK: - 初始化与加载

  /// 初始化服务
  Future<void> init() async {
    logger.i("[标签服务] 初始化");
    await loadTags();
  }

  /// 加载所有标签
  Future<void> loadTags() async {
    _tagModels = TagRepository.all();
    logger.i("[标签服务] 加载了 ${_tagModels.length} 个标签");
  }

  // MARK: - 公共方法

  /// 重新加载标签数据
  Future<void> reload() async => await loadTags();

  /// 清空所有标签
  Future<void> clearAll() async {
    TagRepository.removeAll();
    await loadTags();
    logger.i("[标签服务] 已清空所有标签");
  }

  /// 添加标签到文章
  Future<void> addTagToArticle(ArticleModel article, String tagName) async {
    final success = await TagRepository.addTagToArticle(article, tagName);
    if (success) await reload();
  }

  /// 根据ID查找标签
  TagModel? findById(int id) => TagRepository.findTagModelById(_tagModels, id);

  /// 根据名称查找标签
  TagModel? findByName(String name) => TagRepository.findTagModelByName(_tagModels, name);
}
