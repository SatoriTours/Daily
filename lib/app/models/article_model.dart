import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';

/// 文章数据模型类
///
/// 封装Article实体类，提供属性访问方法
class ArticleModel extends BaseModel<Article> {
  // ==================== 构造函数 ====================

  /// 构造函数
  ArticleModel(super.entity);

  /// 从ID创建实例
  factory ArticleModel.fromId(int id) {
    final article = ArticleRepository.d.findModel(id);
    if (article == null) {
      throw Exception('找不到ID为$id的文章');
    }
    return article;
  }

  // ==================== 重写基类属性 ====================

  /// ID
  @override
  int get id => entity.id;

  /// 创建日期
  @override
  DateTime? get createdAt => entity.createdAt;
  @override
  set createdAt(DateTime? value) => entity.createdAt = value;

  /// 更新日期
  @override
  DateTime? get updatedAt => entity.updatedAt;
  @override
  set updatedAt(DateTime? value) => entity.updatedAt = value;

  // ==================== 基本属性 ====================

  /// 标题
  String? get title => entity.title;
  set title(String? value) => entity.title = value;

  /// AI生成的标题
  String? get aiTitle => entity.aiTitle;
  set aiTitle(String? value) => entity.aiTitle = value;

  /// 内容
  String? get content => entity.content;
  set content(String? value) => entity.content = value;

  /// AI生成的内容
  String? get aiContent => entity.aiContent;
  set aiContent(String? value) => entity.aiContent = value;

  /// HTML格式的内容
  String? get htmlContent => entity.htmlContent;
  set htmlContent(String? value) => entity.htmlContent = value;

  /// AI生成的Markdown内容
  String? get aiMarkdownContent => entity.aiMarkdownContent;
  set aiMarkdownContent(String? value) => entity.aiMarkdownContent = value;

  /// URL
  String? get url => entity.url;
  set url(String? value) => entity.url = value;

  /// 评论
  String? get comment => entity.comment;
  set comment(String? value) => entity.comment = value;

  /// 处理状态
  String get status => entity.status;
  set status(String value) => entity.status = value;

  /// 是否收藏
  bool get isFavorite => entity.isFavorite;
  set isFavorite(bool value) => entity.isFavorite = value;

  /// 发布日期
  DateTime? get pubDate => entity.pubDate;
  set pubDate(DateTime? value) => entity.pubDate = value;

  // ==================== 图片相关属性 ====================

  /// 封面图片路径
  String? get coverImage => entity.coverImage;
  set coverImage(String? value) => entity.coverImage = value;

  /// 封面图片URL
  String? get coverImageUrl => entity.coverImageUrl;
  set coverImageUrl(String? value) => entity.coverImageUrl = value;

  /// 图片列表
  List<Image> get images => entity.images;

  // ==================== 标签相关属性 ====================

  /// 标签列表
  List<Tag> get tags => entity.tags;

  /// 标签ID列表（便于快速过滤）
  List<int> get tagIds => tags.map((t) => t.id).toList();

  // ==================== 计算属性 ====================

  /// 摘要（优先 AI 内容，其次原始内容）
  String get summary => aiContent ?? content ?? '';

  // ==================== 显示方法 ====================

  /// 显示标题
  String showTitle() {
    if (aiTitle != null && aiTitle!.isNotEmpty) {
      return aiTitle!;
    }
    return title ?? '';
  }

  /// 显示内容
  String showContent() {
    if (aiContent != null && aiContent!.isNotEmpty) {
      return aiContent!;
    }
    return content ?? '';
  }

  // ==================== 图片相关方法 ====================

  /// 获取主图路径
  String getHeaderImagePath() {
    if (entity.coverImage != null && entity.coverImage!.isNotEmpty) {
      return entity.coverImage!;
    }
    return '';
  }

  /// 检查是否有主图
  bool hasHeaderImage() {
    return getHeaderImagePath().isNotEmpty || (entity.coverImageUrl != null && entity.coverImageUrl!.isNotEmpty);
  }

  /// 检查是否应该显示头部图片
  bool shouldShowHeaderImage() {
    final path = getHeaderImagePath();
    return (path.isNotEmpty && !path.endsWith('.svg')) ||
        (entity.coverImageUrl != null && entity.coverImageUrl!.isNotEmpty);
  }

  // ==================== 状态管理方法 ====================

  /// 获取处理状态
  String getStatus() {
    return status;
  }

  /// 设置处理状态
  void setStatus(String newStatus) {
    status = newStatus;
  }

  /// 切换收藏状态
  Future<bool> toggleFavorite() async {
    return await ArticleRepository.d.toggleFavorite(id);
  }

  // ==================== 数据操作方法 ====================

  /// 保存模型
  Future<void> save() async {
    await ArticleRepository.d.updateModel(this);
  }

  /// 将其他 ArticleModel 的字段复制到当前实例（保留当前对象引用）
  void copyFrom(ArticleModel other) {
    title = other.title;
    aiTitle = other.aiTitle;
    content = other.content;
    aiContent = other.aiContent;
    htmlContent = other.htmlContent;
    aiMarkdownContent = other.aiMarkdownContent;
    url = other.url;
    isFavorite = other.isFavorite;
    comment = other.comment;
    status = other.status;
    coverImage = other.coverImage;
    coverImageUrl = other.coverImageUrl;
    pubDate = other.pubDate;
    createdAt = other.createdAt;
    updatedAt = other.updatedAt;

    // 同步关联集合（简单替换）
    try {
      tags
        ..clear()
        ..addAll(other.tags);
    } catch (_) {}
    try {
      images
        ..clear()
        ..addAll(other.images);
    } catch (_) {}
  }
}
