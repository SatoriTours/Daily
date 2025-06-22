import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 文章仓储类
///
/// 提供操作文章实体的静态方法集合
class ArticleRepository {
  // 私有构造函数防止实例化
  ArticleRepository._();

  // 获取Box的静态方法
  static Box<Article> get _box => ObjectboxService.i.box<Article>();

  /// 每页文章数量
  static const int pageSize = 10;

  /// 查找所有文章
  static List<ArticleModel> all() {
    return _box.getAll().map((e) => ArticleModel(e)).toList();
  }

  /// 获取所有文章（别名方法）
  static List<ArticleModel> getAll() {
    return all();
  }

  /// 获取每天文章数量统计
  static Map<DateTime, int> getDailyArticleCounts() {
    final counts = <DateTime, int>{};
    final allArticles = ArticleRepository.getAll();

    for (final article in allArticles) {
      final dateKey = DateTime(article.createdAt!.year, article.createdAt!.month, article.createdAt!.day);

      if (counts.containsKey(dateKey)) {
        counts[dateKey] = counts[dateKey]! + 1;
      } else {
        counts[dateKey] = 1;
      }
    }

    return counts;
  }

  /// 分页获取所有文章
  static List<ArticleModel> getAllPaginated(int page) {
    final query = _box.query().order(Article_.id, flags: Order.descending).build();

    try {
      // 计算偏移量
      final offset = (page - 1) * pageSize;
      // 获取查询结果，带分页
      final articles = query.find();
      final paginatedArticles = articles.skip(offset).take(pageSize).toList();
      return paginatedArticles.map((article) => ArticleModel(article)).toList();
    } finally {
      query.close();
    }
  }

  /// 获取文章总数
  static int getTotalCount() {
    return _box.count();
  }

  /// 获取总页数
  static int getTotalPages() {
    final totalItems = getTotalCount();
    return (totalItems / pageSize).ceil();
  }

  /// 根据状态查找文章
  static List<ArticleModel> findByStatus(String status) {
    // 使用数据库查询语句直接获取指定状态的文章
    final query = _box.query(Article_.status.equals(status)).build();
    try {
      final articles = query.find();
      return articles.map((article) => ArticleModel(article)).toList();
    } finally {
      query.close();
    }
  }

  /// 根据多个状态查找文章
  static List<ArticleModel> findByStatuses(List<String> statuses) {
    if (statuses.isEmpty) {
      return [];
    }

    // 构建OR条件: status == status1 OR status == status2 OR ...
    Condition<Article>? condition;
    for (final status in statuses) {
      final statusCondition = Article_.status.equals(status);
      condition = condition == null ? statusCondition : condition.or(statusCondition);
    }

    final query = _box.query(condition).build();
    try {
      final articles = query.find();
      return articles.map((article) => ArticleModel(article)).toList();
    } finally {
      query.close();
    }
  }

  /// 查找所有待处理的文章
  static List<ArticleModel> findAllPending() {
    // 查找状态为pending或webContentFetched的文章
    final pendingArticles = findByStatus(ArticleStatus.pending);
    final webContentFetchedArticles = findByStatus(ArticleStatus.webContentFetched);

    // 合并结果
    return [...pendingArticles, ...webContentFetchedArticles];
  }

  /// 查找所有未完成的文章
  static List<ArticleModel> findAllIncomplete() {
    // 状态是completed但内容不完整的文章
    final List<ArticleModel> result = [];

    // 获取状态为completed但可能内容不完整的文章
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

  /// 按照创建日期到排序，找到最近的一个不为空，而且不是 completed 的文章
  static ArticleModel? findLastPending() {
    final query = _box.query(Article_.status.notEquals('completed').and(Article_.status.notEquals(''))).build();
    final article = query.findFirst();
    return article != null ? ArticleModel(article) : null;
  }

  /// 更新所有 status 为空的文章状态为 pending
  static void updateEmptyStatusToPending() {
    // 查询所有 status 为空的文章
    final query = _box.query(Article_.status.isNull().or(Article_.status.equals(''))).build();
    try {
      final articles = query.find();
      if (articles.isNotEmpty) {
        logger.i("找到 ${articles.length} 篇状态为空的文章，将更新为 pending");

        // 更新状态为 pending
        for (final article in articles) {
          article.status = 'pending';
        }

        _box.putMany(articles);

        logger.i("已将所有状态为空的文章更新为 pending");
      } else {
        logger.i("没有找到状态为空的文章");
      }
    } finally {
      query.close();
    }
  }

  /// 将所有文章状态更新为 completed
  static void updateAllStatusToCompleted() {
    // 查询所有文章
    final query = _box.query().build();
    try {
      final articles = query.find();
      if (articles.isNotEmpty) {
        logger.i("找到 ${articles.length} 篇文章，将全部更新为 completed 状态");

        // 更新状态为 completed
        for (final article in articles) {
          article.status = 'completed';
        }

        _box.putMany(articles);

        logger.i("已将所有文章状态更新为 completed");
      } else {
        logger.i("数据库中没有文章");
      }
    } finally {
      query.close();
    }
  }

  /// 根据ID查找文章
  static ArticleModel? find(int id) {
    // 检查ID是否有效，避免ID=0时的异常
    if (id <= 0) {
      logger.i("查找文章时ID无效: $id");
      return null;
    }

    try {
      final article = _box.get(id);
      return article != null ? ArticleModel(article) : null;
    } catch (e) {
      logger.e("查找文章时发生异常: $e, ID: $id");
      return null;
    }
  }

  /// 创建文章模型
  static ArticleModel createArticleModel(Map<String, dynamic> data) {
    final article = Article(
      title: data['title'],
      aiTitle: data['aiTitle'],
      content: data['content'],
      aiContent: data['aiContent'],
      htmlContent: data['htmlContent'],
      aiMarkdownContent: data['aiMarkdownContent'],
      url: data['url'],
      pubDate: data['pubDate'],
      createdAt: data['createdAt'] ?? DateTime.now().toUtc(),
      updatedAt: data['updatedAt'] ?? DateTime.now().toUtc(),
      comment: data['comment'],
      status: data['status'] ?? 'pending',
    );

    return ArticleModel(article);
  }

  /// 更新文章ID
  static void updateArticleId(ArticleModel articleModel, int id) {
    articleModel.entity.id = id;
  }

  /// 根据URL获取第一篇文章
  static Future<ArticleModel?> findByUrl(String url) async {
    final query = _box.query(Article_.url.equals(url)).build();
    final article = query.findFirst();
    query.close();
    return article != null ? ArticleModel(article) : null;
  }

  /// 检查文章是否存在
  static Future<bool> isArticleExists(String url) async {
    return await findByUrl(url) != null;
  }

  /// 删除文章及其关联数据
  static Future<void> deleteArticle(int id) async {
    final articleModel = find(id);
    if (articleModel == null) {
      logger.i("未找到文章以删除: $id");
      return;
    }

    // 清理关联数据
    articleModel.tags.clear();
    articleModel.images.clear();
    articleModel.screenshots.clear();

    // 保存更改并删除文章
    await update(articleModel);
    destroy(id);

    logger.i("文章已删除: $id");
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

  /// 分页搜索文章
  static List<ArticleModel> wherePaginated({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
    int page = 1,
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

    // 构建查询
    final query = queryBuilder.build();
    try {
      // 计算偏移量
      final offset = (page - 1) * pageSize;
      // 获取查询结果，带分页
      final articles = query.find();
      final paginatedArticles = articles.skip(offset).take(pageSize).toList();
      return paginatedArticles.map((article) => ArticleModel(article)).toList();
    } finally {
      query.close();
    }
  }

  /// 获取搜索结果的总数
  static int getSearchCount({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
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

    // 构建查询并计算总数
    final query = queryBuilder.build();
    try {
      return query.count();
    } finally {
      query.close();
    }
  }

  /// 获取搜索结果的总页数
  static int getSearchTotalPages({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
  }) {
    final totalItems = getSearchCount(
      keyword: keyword,
      tagIds: tagIds,
      isFavorite: isFavorite,
      startDate: startDate,
      endDate: endDate,
      referenceId: referenceId,
      isGreaterThan: isGreaterThan,
    );
    return (totalItems / pageSize).ceil();
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
      // 当前查询标签的实现需要引用Tag_类，但我们正在尝试移除所有对Tag的直接引用
      // 可能的解决方案：
      // 1. 使用原生SQL查询重写此功能
      // 2. 使用两步查询：先获取标签ID列表，再查询文章
      // 3. 实现ArticleModel.containsTagId(int tagId)方法在应用层过滤

      // 暂时禁用标签过滤，应当在完成重构后移除此注释
      // 原代码：
      // for (final tagId in tagIds) {
      //   queryBuilder.linkMany(Article_.tags, Tag_.id.equals(tagId));
      // }
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
    try {
      final id = await _box.putAsync(articleModel.entity);
      logger.i("文章已保存: ${StringUtils.firstLine(articleModel.title ?? '')}");
      return id;
    } catch (e) {
      logger.e("[保存文章失败] $e");
      return 0;
    }
  }

  /// 更新文章
  static Future<int> update(ArticleModel articleModel) async {
    try {
      final id = await _box.putAsync(articleModel.entity);
      logger.i("文章已更新: ${StringUtils.firstLine(articleModel.title ?? '')}");
      return id;
    } catch (e) {
      logger.e("[更新文章失败] $e");
      return 0;
    }
  }

  /// 删除文章
  static bool destroy(int id) {
    return _box.remove(id);
  }

  /// 切换收藏状态
  static Future<bool> toggleFavorite(int id) async {
    final articleModel = find(id);
    if (articleModel == null) return false;

    articleModel.isFavorite = !articleModel.isFavorite;
    await update(articleModel);

    final status = articleModel.isFavorite ? "已收藏" : "已取消收藏";
    logger.i("文章$status: $id");
    return articleModel.isFavorite;
  }

  /// 更新文章单个字段
  ///
  /// 只更新指定字段，避免覆盖其他属性
  static Future<bool> updateField(int articleId, ArticleFieldName fieldName, String fieldValue) async {
    try {
      // 获取文章
      final article = _box.get(articleId);
      if (article == null) {
        logger.e("更新字段失败：未找到文章 ID=$articleId");
        return false;
      }

      // 根据字段类型更新不同属性
      switch (fieldName.name) {
        case 'title':
          article.title = fieldValue;
          break;
        case 'aiTitle':
          article.aiTitle = fieldValue;
          break;
        case 'content':
          article.content = fieldValue;
          break;
        case 'aiContent':
          article.aiContent = fieldValue;
          break;
        case 'htmlContent':
          article.htmlContent = fieldValue;
          break;
        case 'aiMarkdownContent':
          article.aiMarkdownContent = fieldValue;
          break;
        case 'coverImage':
          article.coverImage = fieldValue;
          break;
        case 'coverImageUrl':
          article.coverImageUrl = fieldValue;
          break;
        case 'status':
          article.status = fieldValue;
          break;
        default:
          logger.w("不支持更新字段：$fieldName");
          return false;
      }

      // 更新时间戳
      article.updatedAt = DateTime.now().toUtc();

      // 保存文章
      await _box.putAsync(article);
      logger.d("文章 ID=$articleId 的字段 $fieldName 已更新");
      return true;
    } catch (e) {
      logger.e("更新文章字段失败：$e");
      return false;
    }
  }

  /// 添加标签到文章
  static Future<bool> addTagToArticle(int articleId, int tagId) async {
    try {
      // 获取文章
      final article = _box.get(articleId);
      if (article == null) {
        logger.e("添加标签失败：未找到文章 ID=$articleId");
        return false;
      }

      // 通过TagRepository获取标签模型
      final tagModel = TagRepository.find(tagId);
      if (tagModel == null) {
        logger.e("添加标签失败：未找到标签 ID=$tagId");
        return false;
      }

      // 添加标签到文章 - 通过标签模型获取实体
      article.tags.add(tagModel.entity);

      // 保存文章
      await _box.putAsync(article);
      logger.i("已添加标签 ID=$tagId 到文章 ID=$articleId");
      return true;
    } catch (e) {
      logger.e("添加标签到文章失败：$e");
      return false;
    }
  }

  /// 从文章中移除标签
  static Future<bool> removeTagFromArticle(int articleId, int tagId) async {
    try {
      // 获取文章
      final article = _box.get(articleId);
      if (article == null) {
        logger.e("移除标签失败：未找到文章 ID=$articleId");
        return false;
      }

      // 通过TagRepository获取标签模型
      final tagModel = TagRepository.find(tagId);
      if (tagModel == null) {
        logger.e("移除标签失败：未找到标签 ID=$tagId");
        return false;
      }

      // 从文章移除标签 - 通过标签模型获取实体
      article.tags.remove(tagModel.entity);

      // 保存文章
      await _box.putAsync(article);
      logger.i("已从文章 ID=$articleId 移除标签 ID=$tagId");
      return true;
    } catch (e) {
      logger.e("从文章移除标签失败：$e");
      return false;
    }
  }
}

/// 获取文章数据库字段名
class ArticleFieldName {
  final String name;

  const ArticleFieldName({required this.name});

  static const ArticleFieldName id = ArticleFieldName(name: "id");
  static const ArticleFieldName title = ArticleFieldName(name: "title");
  static const ArticleFieldName aiTitle = ArticleFieldName(name: "aiTitle");
  static const ArticleFieldName content = ArticleFieldName(name: "content");
  static const ArticleFieldName htmlContent = ArticleFieldName(name: "htmlContent");
  static const ArticleFieldName aiContent = ArticleFieldName(name: "aiContent");
  static const ArticleFieldName url = ArticleFieldName(name: "url");
  static const ArticleFieldName isFavorite = ArticleFieldName(name: "isFavorite");
  static const ArticleFieldName comment = ArticleFieldName(name: "comment");
  static const ArticleFieldName pubDate = ArticleFieldName(name: "pubDate");
  static const ArticleFieldName coverImage = ArticleFieldName(name: "coverImage");
  static const ArticleFieldName aiMarkdownContent = ArticleFieldName(name: "aiMarkdownContent");
  static const ArticleFieldName coverImageUrl = ArticleFieldName(name: "coverImageUrl");

  static const ArticleFieldName status = ArticleFieldName(name: "status");
  static const ArticleFieldName createdAt = ArticleFieldName(name: "createdAt");
  static const ArticleFieldName updatedAt = ArticleFieldName(name: "updatedAt");
}

/// 文章状态常量类
class ArticleStatus {
  // 私有构造函数防止实例化
  ArticleStatus._();

  /// 待处理状态
  static const String pending = 'pending';

  /// 网页内容已获取状态
  static const String webContentFetched = 'web_content_fetched';

  /// 处理完成状态
  static const String completed = 'completed';

  /// 错误状态
  static const String error = 'error';
}
