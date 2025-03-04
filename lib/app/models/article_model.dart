import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 文章模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class ArticleModel extends BaseModel<Article> {
  // 单例实现
  static final ArticleModel _instance = ArticleModel._internal();
  static ArticleModel get i => _instance;
  factory ArticleModel() => _instance;
  ArticleModel._internal() : super.withEntity(null);

  /// 构造函数，接收一个Article实体
  ArticleModel.withEntity(Article? article) : super.withEntity(article);

  @override
  int get id => entity?.id ?? 0;

  /// 文章标题
  String? get title => entity?.title;

  /// AI生成的标题
  String? get aiTitle => entity?.aiTitle;

  /// 文章内容
  String? get content => entity?.content;

  /// AI生成的内容
  String? get aiContent => entity?.aiContent;

  /// HTML格式的内容
  String? get htmlContent => entity?.htmlContent;

  /// 文章URL
  String? get url => entity?.url;

  /// 是否收藏
  bool get isFavorite => entity?.isFavorite ?? false;

  /// 评论
  String? get comment => entity?.comment;

  /// 发布日期
  DateTime? get pubDate => entity?.pubDate;

  /// 更新日期
  DateTime? get updatedAt => entity?.updatedAt;

  /// 创建日期
  DateTime? get createdAt => entity?.createdAt;

  /// 获取文章主图路径
  String get headerImagePath {
    return entity?.images.isEmpty ?? true ? '' : (entity!.images.first.path ?? '');
  }

  /// 检查文章是否有主图
  bool get hasHeaderImage => headerImagePath.isNotEmpty;

  /// 检查是否应该显示头部图片
  bool get shouldShowHeaderImage {
    return hasHeaderImage && !headerImagePath.endsWith('.svg');
  }

  @override
  ArticleModel _createFromEntity(Article entity) {
    return ArticleModel.withEntity(entity);
  }

  @override
  Future<int> _saveEntity(Article entity) async {
    return await box.putAsync(entity);
  }

  /// 静态方法 - 从实体创建模型实例
  static ArticleModel fromEntity(Article article) {
    return ArticleModel.withEntity(article);
  }

  /// 静态方法 - 查找所有文章
  static List<ArticleModel> all() {
    return i.findAll().cast<ArticleModel>();
  }

  /// 静态方法 - 根据ID查找文章
  static ArticleModel? find(int id) {
    return i.findById(id) as ArticleModel?;
  }

  /// 静态方法 - 搜索文章
  static List<ArticleModel> where({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
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

    // 创建查询构建器
    final queryBuilder = condition == null ? i.box.query() : i.box.query(condition);

    // 标签过滤
    if (tagIds != null && tagIds.isNotEmpty) {
      for (var tagId in tagIds) {
        queryBuilder.linkMany(Article_.tags, Tag_.id.equals(tagId));
      }
    }

    // 排序
    queryBuilder.order(Article_.id, flags: Order.descending);

    // 执行查询
    final articles = queryBuilder.build().find();

    // 转换为模型列表
    return articles.map((article) => ArticleModel.fromEntity(article)).toList();
  }

  /// 静态方法 - 保存文章
  static Future<int> create(ArticleModel model) async {
    return await i.saveModel(model);
  }

  /// 静态方法 - 删除文章
  static bool destroy(int id) {
    return i.deleteById(id);
  }
}
