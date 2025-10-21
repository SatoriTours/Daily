import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 文章仓储类
///
/// 使用单例模式提供数据访问功能
/// 通过 .d 访问器调用: ArticleRepository.d.method()
/// d 代表 database/data，简洁易记
class ArticleRepository extends BaseRepository<Article, ArticleModel> {
  // 私有构造函数
  ArticleRepository._();

  // 单例实例 - 使用 d 作为访问器 (database/data)
  static final ArticleRepository d = ArticleRepository._();

  // 每页文章数量
  @override
  int get pageSize => 10;

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  ArticleModel toModel(Article entity) {
    return ArticleModel(entity);
  }

  @override
  Article toEntity(ArticleModel model) {
    return model.entity;
  }

  @override
  DateTime? extractDateFromModel(ArticleModel model) {
    return model.createdAt;
  }

  // ==================== 特定业务方法 ====================

  /// 根据状态查找文章
  List<ArticleModel> findByStatus(String status) {
    final articles = findByStringEquals(Article_.status, status);
    return articles.map((article) => ArticleModel(article)).toList();
  }

  /// 根据多个状态查找文章
  List<ArticleModel> findByStatuses(List<String> statuses) {
    if (statuses.isEmpty) {
      return [];
    }

    // 构建OR条件
    Condition<Article>? condition;
    for (final status in statuses) {
      final statusCondition = Article_.status.equals(status);
      condition = condition == null ? statusCondition : condition.or(statusCondition);
    }

    final articles = findByCondition(condition!);
    return articles.map((article) => ArticleModel(article)).toList();
  }

  /// 查找所有待处理的文章
  List<ArticleModel> findAllPending() {
    final pendingArticles = findByStatus(ArticleStatus.pending);
    final webContentFetchedArticles = findByStatus(ArticleStatus.webContentFetched);
    return [...pendingArticles, ...webContentFetchedArticles];
  }

  /// 查找所有未完成的文章
  List<ArticleModel> findAllIncomplete() {
    final List<ArticleModel> result = [];
    final completedArticles = findByStatus(ArticleStatus.completed);

    for (final article in completedArticles) {
      final bool titleComplete = article.aiTitle != null && article.aiTitle!.isNotEmpty;
      final bool summaryComplete = article.aiContent != null && article.aiContent!.isNotEmpty;
      final bool markdownComplete = article.aiMarkdownContent != null && article.aiMarkdownContent!.isNotEmpty;
      final bool coverComplete =
          article.coverImageUrl == null ||
          article.coverImageUrl!.isEmpty ||
          (article.coverImage != null && article.coverImage!.isNotEmpty);

      if (!titleComplete || !summaryComplete || !markdownComplete || !coverComplete) {
        result.add(article);
      }
    }

    return result;
  }

  /// 找到最近的一个待处理文章
  ArticleModel? findLastPending() {
    final condition = Article_.status.notEquals('completed').and(Article_.status.notEquals(''));
    final article = findFirstByCondition(condition);
    return article != null ? ArticleModel(article) : null;
  }

  /// 更新所有空状态的文章为 pending
  void updateEmptyStatusToPending() {
    final condition = Article_.status.isNull().or(Article_.status.equals(''));
    final articles = findByCondition(condition);

    if (articles.isNotEmpty) {
      logger.i("找到 ${articles.length} 篇状态为空的文章,将更新为 pending");
      for (final article in articles) {
        article.status = 'pending';
      }
      saveMany(articles);
      logger.i("已将所有状态为空的文章更新为 pending");
    } else {
      logger.i("没有找到状态为空的文章");
    }
  }

  /// 将所有文章状态更新为 completed
  void updateAllStatusToCompleted() {
    final articles = all();

    if (articles.isNotEmpty) {
      logger.i("找到 ${articles.length} 篇文章,将全部更新为 completed 状态");
      for (final article in articles) {
        article.status = 'completed';
      }
      saveMany(articles);
      logger.i("已将所有文章状态更新为 completed");
    } else {
      logger.i("数据库中没有文章");
    }
  }

  /// 根据URL查找文章
  Future<ArticleModel?> findByUrl(String url) async {
    final article = findFirstByStringEquals(Article_.url, url);
    return article != null ? ArticleModel(article) : null;
  }

  /// 根据URL判断文章是否存在
  Future<bool> existsByUrl(String url) async {
    return await findByUrl(url) != null;
  }

  /// 删除文章及其关联数据
  Future<void> deleteArticle(int id) async {
    final articleModel = findModel(id);
    if (articleModel == null) {
      logger.i("未找到文章以删除: $id");
      return;
    }

    // 清理关联数据
    articleModel.tags.clear();
    articleModel.images.clear();

    // 保存更改并删除文章
    await updateModel(articleModel);
    remove(id);

    logger.i("文章已删除: $id");
  }

  /// 保存文章Model（带日志）
  @override
  Future<int> saveModel(ArticleModel articleModel) async {
    try {
      final id = await super.saveModel(articleModel);
      logger.i("文章已保存: ${StringUtils.firstLine(articleModel.title ?? '')}");
      return id;
    } catch (e) {
      logger.e("[保存文章失败] $e");
      return 0;
    }
  }

  /// 更新文章Model（带日志）
  @override
  Future<int> updateModel(ArticleModel articleModel) async {
    try {
      final id = await super.updateModel(articleModel);
      logger.i("文章已更新: ${StringUtils.firstLine(articleModel.title ?? '')}");
      return id;
    } catch (e) {
      logger.e("[更新文章失败] $e");
      return 0;
    }
  }

  /// 切换收藏状态
  Future<bool> toggleFavorite(int id) async {
    final articleModel = findModel(id);
    if (articleModel == null) return false;

    articleModel.isFavorite = !articleModel.isFavorite;
    await updateModel(articleModel);

    final status = articleModel.isFavorite ? "已收藏" : "已取消收藏";
    logger.i("文章$status: $id");
    return articleModel.isFavorite;
  }

  /// 根据状态统计文章数量
  int countByStatus(String status) {
    return countByCondition(Article_.status.equals(status));
  }

  /// 更新文章的单个字段
  Future<void> updateField(int articleId, ArticleFieldName fieldName, dynamic value) async {
    final article = find(articleId);
    if (article == null) {
      logger.w('[文章仓储] 更新字段失败: 文章不存在 #$articleId');
      return;
    }

    switch (fieldName) {
      case ArticleFieldName.status:
        article.status = value as String;
        break;
      case ArticleFieldName.aiTitle:
        article.aiTitle = value as String?;
        break;
      case ArticleFieldName.aiContent:
        article.aiContent = value as String?;
        break;
      case ArticleFieldName.aiMarkdownContent:
        article.aiMarkdownContent = value as String?;
        break;
      case ArticleFieldName.coverImage:
        article.coverImage = value as String?;
        break;
    }

    save(article);
  }

  /// 获取搜索结果数量
  int getSearchCount(String query) {
    return countByCondition(Article_.title.contains(query, caseSensitive: false));
  }

  /// 获取搜索结果总页数
  int getSearchTotalPages(String query) {
    final count = getSearchCount(query);
    return (count / pageSize).ceil();
  }

  /// 复杂条件查询文章(支持多个过滤条件)
  List<ArticleModel> queryArticles({
    String? keyword,
    bool? isFavorite,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
    int? limit,
  }) {
    Condition<Article>? condition;

    // 构建查询条件
    if (keyword != null && keyword.isNotEmpty) {
      final keywordCondition = Article_.title
          .contains(keyword, caseSensitive: false)
          .or(Article_.content.contains(keyword, caseSensitive: false));
      condition = keywordCondition;
    }

    if (isFavorite != null) {
      final favoriteCondition = Article_.isFavorite.equals(isFavorite);
      condition = condition == null ? favoriteCondition : condition.and(favoriteCondition);
    }

    if (startDate != null) {
      final startCondition = Article_.createdAt.greaterOrEqual(startDate.millisecondsSinceEpoch);
      condition = condition == null ? startCondition : condition.and(startCondition);
    }

    if (endDate != null) {
      final endCondition = Article_.createdAt.lessOrEqual(endDate.millisecondsSinceEpoch);
      condition = condition == null ? endCondition : condition.and(endCondition);
    }

    if (referenceId != null) {
      final idCondition = isGreaterThan == true
          ? Article_.id.greaterThan(referenceId)
          : Article_.id.lessThan(referenceId);
      condition = condition == null ? idCondition : condition.and(idCondition);
    }

    // 执行查询
    final query = condition != null
        ? box.query(condition).order(Article_.id, flags: Order.descending).build()
        : box.query().order(Article_.id, flags: Order.descending).build();

    if (limit != null) {
      query.limit = limit;
    }

    try {
      final articles = query.find();
      return articles.map((article) => ArticleModel(article)).toList();
    } finally {
      query.close();
    }
  }

  /// 分页复杂条件查询
  List<ArticleModel> queryArticlesPaginated({String? keyword, int page = 1}) {
    Condition<Article>? condition;

    if (keyword != null && keyword.isNotEmpty) {
      condition = Article_.title
          .contains(keyword, caseSensitive: false)
          .or(Article_.content.contains(keyword, caseSensitive: false));
    }

    final query = condition != null
        ? box.query(condition).order(Article_.id, flags: Order.descending).build()
        : box.query().order(Article_.id, flags: Order.descending).build();

    query
      ..limit = pageSize
      ..offset = (page - 1) * pageSize;

    try {
      final articles = query.find();
      return articles.map((article) => ArticleModel(article)).toList();
    } finally {
      query.close();
    }
  }
}

/// 文章状态常量类
class ArticleStatus {
  ArticleStatus._();

  static const String pending = 'pending';
  static const String webContentFetched = 'web_content_fetched';
  static const String completed = 'completed';
  static const String error = 'error';
}

/// 文章字段名称枚举
enum ArticleFieldName { status, aiTitle, aiContent, aiMarkdownContent, coverImage }
