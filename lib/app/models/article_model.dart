import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 文章模型类
///
/// 采用Rails风格的Model设计，同时作为领域模型和数据访问层
/// 包含实体属性访问和数据操作方法，遵循活动记录模式(Active Record Pattern)
class ArticleModel {
  // 单例实现
  static final ArticleModel _instance = ArticleModel._internal();
  static ArticleModel get i => _instance;
  factory ArticleModel() => _instance;
  ArticleModel._internal();

  // ObjectBox服务和Box访问
  final _objectboxService = ObjectboxService.i;
  Box<Article> get _articleBox => _objectboxService.box<Article>();

  // 实体对象
  Article? _article;

  /// 构造函数，接收一个Article实体
  ArticleModel.withEntity(this._article);

  /// 获取原始Article实体
  Article? get entity => _article;

  /// 文章ID
  int get id => _article?.id ?? 0;

  /// 文章标题
  String? get title => _article?.title;

  /// AI生成的标题
  String? get aiTitle => _article?.aiTitle;

  /// 文章内容
  String? get content => _article?.content;

  /// AI生成的内容
  String? get aiContent => _article?.aiContent;

  /// HTML格式的内容
  String? get htmlContent => _article?.htmlContent;

  /// 文章URL
  String? get url => _article?.url;

  /// 是否收藏
  bool get isFavorite => _article?.isFavorite ?? false;

  /// 评论
  String? get comment => _article?.comment;

  /// 发布日期
  DateTime? get pubDate => _article?.pubDate;

  /// 更新日期
  DateTime? get updatedAt => _article?.updatedAt;

  /// 创建日期
  DateTime? get createdAt => _article?.createdAt;

  /// 获取文章主图路径
  String get headerImagePath {
    return _article?.images.isEmpty ?? true ? '' : (_article!.images.first.path ?? '');
  }

  /// 检查文章是否有主图
  bool get hasHeaderImage => headerImagePath.isNotEmpty;

  /// 检查是否应该显示头部图片
  bool get shouldShowHeaderImage {
    return hasHeaderImage && !headerImagePath.endsWith('.svg');
  }

  /// 静态方法 - 从实体创建模型实例
  static ArticleModel fromEntity(Article article) {
    return ArticleModel.withEntity(article);
  }

  /// 静态方法 - 查找所有文章
  static List<ArticleModel> all() {
    return i._findAll();
  }

  /// 静态方法 - 根据ID查找文章
  static ArticleModel? find(int id) {
    return i._findById(id);
  }

  /// 静态方法 - 搜索文章
  static List<ArticleModel> where({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
  }) {
    return i._findWhere(
      keyword: keyword,
      tagIds: tagIds,
      isFavorite: isFavorite,
      startDate: startDate,
      endDate: endDate,
    );
  }

  /// 静态方法 - 保存文章
  static Future<int> create(ArticleModel model) async {
    return await i._save(model);
  }

  /// 静态方法 - 删除文章
  static bool destroy(int id) {
    return i._delete(id);
  }

  /// 实例方法 - 保存当前模型
  Future<int> save() async {
    if (_article == null) return 0;
    return await ArticleModel.create(this);
  }

  /// 实例方法 - 删除当前模型
  bool delete() {
    if (_article == null) return false;
    return ArticleModel.destroy(id);
  }

  // 私有方法 - 查找所有文章
  List<ArticleModel> _findAll() {
    final articles = _articleBox.getAll();
    return _fromEntityList(articles);
  }

  // 私有方法 - 根据ID查找文章
  ArticleModel? _findById(int id) {
    final article = _articleBox.get(id);
    return article != null ? ArticleModel.fromEntity(article) : null;
  }

  // 私有方法 - 保存文章
  Future<int> _save(ArticleModel model) async {
    if (model._article == null) return 0;
    return await _articleBox.putAsync(model._article!);
  }

  // 私有方法 - 删除文章
  bool _delete(int id) {
    return _articleBox.remove(id);
  }

  // 私有方法 - 搜索文章
  List<ArticleModel> _findWhere({
    String? keyword,
    List<int>? tagIds,
    bool? isFavorite,
    DateTime? startDate,
    DateTime? endDate,
  }) {
    // 构建查询条件
    List<Condition<Article>> conditions = [];

    // 处理关键词搜索
    if (keyword != null && keyword.isNotEmpty) {
      conditions.add(Article_.title.contains(keyword) | Article_.content.contains(keyword));
    }

    // 处理收藏过滤
    if (isFavorite != null) {
      conditions.add(Article_.isFavorite.equals(isFavorite));
    }

    // 处理日期范围
    if (startDate != null) {
      conditions.add(Article_.pubDate.greaterThan(startDate.millisecondsSinceEpoch));
    }
    if (endDate != null) {
      conditions.add(Article_.pubDate.lessThan(endDate.millisecondsSinceEpoch));
    }

    // 组合条件
    Condition<Article>? finalCondition;
    for (var condition in conditions) {
      finalCondition = finalCondition == null ? condition : finalCondition & condition;
    }

    // 构建并执行查询
    final query = _articleBox.query(finalCondition);

    // 处理标签过滤
    if (tagIds != null && tagIds.isNotEmpty) {
      for (var tagId in tagIds) {
        query.linkMany(Article_.tags, Tag_.id.equals(tagId));
      }
    }

    // 设置排序
    query.order(Article_.updatedAt, flags: Order.descending);

    final articles = query.build().find();
    return _fromEntityList(articles);
  }

  // 私有方法 - 将实体列表转换为模型列表
  List<ArticleModel> _fromEntityList(List<Article> articles) {
    return articles.map((article) => ArticleModel.fromEntity(article)).toList();
  }
}
