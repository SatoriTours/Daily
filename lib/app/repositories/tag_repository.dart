import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/models/tag_model.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 标签仓储类
///
/// 提供操作标签实体的静态方法集合
class TagRepository {
  // 私有构造函数防止实例化
  TagRepository._();

  // 获取Box的静态方法
  static Box<Tag> get _box => ObjectboxService.i.box<Tag>();

  /// 查找所有标签
  static List<TagModel> all() {
    return _box.getAll().map((e) => TagModel(e)).toList();
  }

  /// 根据ID查找标签
  static TagModel? find(int id) {
    final tag = _box.get(id);
    return tag != null ? TagModel(tag) : null;
  }

  /// 根据名称查找标签
  static TagModel? findByName(String name) {
    final query = _box.query(Tag_.name.equals(name)).build();
    final tag = query.findFirst();
    query.close();
    return tag != null ? TagModel(tag) : null;
  }

  /// 根据ID从标签列表中查找标签
  static TagModel? findTagModelById(List<TagModel> tagModels, int id) {
    try {
      return tagModels.firstWhere((tag) => tag.id == id);
    } catch (e) {
      return null;
    }
  }

  /// 根据名称从标签列表中查找标签
  static TagModel? findTagModelByName(List<TagModel> tagModels, String name) {
    try {
      return tagModels.firstWhere((tag) => tag.name == name);
    } catch (e) {
      return null;
    }
  }

  /// 添加标签到文章
  static Future<bool> addTagToArticle(ArticleModel articleModel, String tagName) async {
    try {
      // 获取或创建标签
      final tagModel = findOrCreate(tagName);

      // 获取文章实体
      final article = articleModel.entity;

      // 添加标签到文章
      article.tags.add(tagModel.entity);

      // 立即持久化变更，避免后续仅更新单字段导致关系未保存
      await ObjectboxService.i.box<Article>().putAsync(article);

      logger.i("[添加标签] 已添加标签 '$tagName' 到文章 ${articleModel.title}");
      return true;
    } catch (e) {
      logger.e("[添加标签] 失败: $e");
      return false;
    }
  }

  /// 为文章设置完整的标签集合（覆盖式），并持久化
  static Future<void> setTagsForArticle(int articleId, List<String> tagNames) async {
    try {
      final articleBox = ObjectboxService.i.box<Article>();
      final article = articleBox.get(articleId);
      if (article == null) return;

      // 清空并重建标签集合
      article.tags.clear();
      for (final name in tagNames) {
        final tag = findOrCreate(name).entity;
        article.tags.add(tag);
      }

      // 仅基于最新实体持久化，避免覆盖其它并发更新字段
      await articleBox.putAsync(article);
    } catch (e) {
      logger.e('[设置文章标签] 失败: $e');
    }
  }

  /// 创建或查找标签
  static TagModel findOrCreate(String name, {String? icon}) {
    var tagModel = findByName(name);
    if (tagModel != null) return tagModel;

    final tag = Tag(name: name, icon: icon);
    final id = _box.put(tag);
    tag.id = id;
    return TagModel(tag);
  }

  /// 保存标签
  static Future<int> create(TagModel tagModel) async {
    return await _box.putAsync(tagModel.entity);
  }

  /// 更新标签
  static Future<int> update(TagModel tagModel) async {
    return await _box.putAsync(tagModel.entity);
  }

  /// 删除标签
  static bool destroy(int id) {
    return _box.remove(id);
  }

  /// 删除所有标签
  static void removeAll() {
    _box.removeAll();
    logger.i("[删除标签] 所有标签已删除");
  }

  /// 根据文章ID删除标签
  static int deleteByArticleId(int articleId) {
    return 0;
  }
}
