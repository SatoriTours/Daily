import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/models/article_model.dart';
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
    // 创建查询构建器
    final queryBuilder = _createQueryBuilder(
      keyword: keyword,
      isFavorite: isFavorite,
      startDate: startDate,
      endDate: endDate,
      referenceId: referenceId,
      isGreaterThan: isGreaterThan,
    );

    // 应用标签过滤
    _applyTagFilter(queryBuilder, tagIds);

    // 应用排序
    queryBuilder.order(Article_.id, flags: Order.descending);

    // 执行查询
    return _executeQuery(queryBuilder, pageSize);
  }

  /// 创建查询构建器并应用基本条件
  static QueryBuilder<Article> _createQueryBuilder({
    String? keyword,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
  }) {
    // 组合所有条件
    final conditions = <Condition<Article>>[];

    // 添加关键词条件
    if (keyword != null && keyword.isNotEmpty) {
      conditions.add(
        Article_.title.contains(keyword) |
            Article_.aiTitle.contains(keyword) |
            Article_.content.contains(keyword) |
            Article_.aiContent.contains(keyword) |
            Article_.comment.contains(keyword),
      );
    }

    // 添加收藏条件
    if (isFavorite != null) {
      conditions.add(Article_.isFavorite.equals(isFavorite));
    }

    // 添加日期条件
    if (startDate != null) {
      conditions.add(Article_.createdAt.greaterThan(startDate.millisecondsSinceEpoch));
    }

    if (endDate != null) {
      conditions.add(Article_.createdAt.lessThan(endDate.millisecondsSinceEpoch));
    }

    // 添加ID条件
    if (referenceId != null && isGreaterThan != null) {
      conditions.add(isGreaterThan ? Article_.id.greaterThan(referenceId) : Article_.id.lessThan(referenceId));
    }

    // 合并所有条件
    Condition<Article>? finalCondition;
    for (final condition in conditions) {
      finalCondition = finalCondition == null ? condition : finalCondition & condition;
    }

    // 创建查询构建器
    return finalCondition == null ? _box.query() : _box.query(finalCondition);
  }

  /// 应用标签过滤
  static void _applyTagFilter(QueryBuilder<Article> queryBuilder, List<int>? tagIds) {
    if (tagIds != null && tagIds.isNotEmpty) {
      for (final tagId in tagIds) {
        queryBuilder.linkMany(Article_.tags, Tag_.id.equals(tagId));
      }
    }
  }

  /// 执行查询并返回结果
  static List<ArticleModel> _executeQuery(QueryBuilder<Article> queryBuilder, int pageSize) {
    final query = queryBuilder.build();

    // 设置分页
    if (pageSize > 0) {
      query.limit = pageSize;
    }

    // 执行查询
    return query.find().map((e) => ArticleModel(e)).toList();
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
