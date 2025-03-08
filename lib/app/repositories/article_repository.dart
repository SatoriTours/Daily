import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 文章仓储类
///
/// 提供操作文章实体的静态方法集合
class ArticleRepository {
  // 私有构造函数防止实例化
  ArticleRepository._();

  // 获取Box的静态方法
  static Box<Article> get _box => ObjectboxService.i.box<Article>();

  /// 查找所有文章
  static List<ArticleModel> all() {
    return _box.getAll().map((e) => ArticleModel(e)).toList();
  }

  /// 根据ID查找文章
  static ArticleModel? find(int id) {
    final article = _box.get(id);
    return article != null ? ArticleModel(article) : null;
  }

  /// 搜索文章
  static List<ArticleModel> where({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
    int pageSize = 20,
  }) {
    // 构建查询条件
    Condition<Article>? condition;

    // 关键词条件
    if (keyword != null && keyword.isNotEmpty) {
      condition =
          Article_.title.contains(keyword) |
          Article_.aiTitle.contains(keyword) |
          Article_.content.contains(keyword) |
          Article_.aiContent.contains(keyword) |
          Article_.comment.contains(keyword);
    }

    // 收藏条件
    if (isFavorite != null) {
      final favoriteCondition = Article_.isFavorite.equals(isFavorite);
      condition = condition == null ? favoriteCondition : condition & favoriteCondition;
    }

    // 开始日期条件
    if (startDate != null) {
      final startDateCondition = Article_.createdAt.greaterThan(startDate.millisecondsSinceEpoch);
      condition = condition == null ? startDateCondition : condition & startDateCondition;
    }

    // 结束日期条件
    if (endDate != null) {
      final endDateCondition = Article_.createdAt.lessThan(endDate.millisecondsSinceEpoch);
      condition = condition == null ? endDateCondition : condition & endDateCondition;
    }

    // ID参考条件（用于分页加载更多/之前的内容）
    if (referenceId != null && isGreaterThan != null) {
      final idCondition = isGreaterThan ? Article_.id.greaterThan(referenceId) : Article_.id.lessThan(referenceId);
      condition = condition == null ? idCondition : condition & idCondition;
    }

    // 创建查询构建器
    final queryBuilder = condition == null ? _box.query() : _box.query(condition);

    // 标签过滤
    if (tagIds != null && tagIds.isNotEmpty) {
      for (var tagId in tagIds) {
        queryBuilder.linkMany(Article_.tags, Tag_.id.equals(tagId));
      }
    }

    // 排序
    queryBuilder.order(Article_.id, flags: Order.descending);

    // 执行查询并设置结果数量限制
    final query = queryBuilder.build();
    if (pageSize > 0) {
      query.limit = pageSize;
    }

    // 执行查询并返回结果
    final articles = query.find();
    return articles.map((e) => ArticleModel(e)).toList();
  }

  /// 保存文章
  static Future<int> create(ArticleModel articleModel) async {
    return await _box.putAsync(articleModel.entity);
  }

  /// 更新文章
  static Future<int> update(ArticleModel articleModel) async {
    return await _box.putAsync(articleModel.entity);
  }

  /// 删除文章
  static bool destroy(int id) {
    return _box.remove(id);
  }

  /// 切换收藏状态
  static Future<bool> toggleFavorite(int id) async {
    final articleModel = find(id);
    if (articleModel == null) return false;

    final entity = articleModel.entity;
    entity.isFavorite = !entity.isFavorite;
    await _box.putAsync(entity);
    return entity.isFavorite;
  }
}
