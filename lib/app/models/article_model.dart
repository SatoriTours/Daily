import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';

/// 文章数据模型类
class ArticleModel extends EntityModel<Article> {
  ArticleModel(super.entity);

  factory ArticleModel.fromId(int id) {
    final article = ArticleRepository.d.findModel(id);
    if (article == null) {
      throw Exception('找不到ID为$id的文章');
    }
    return article;
  }

  @override
  int get id => entity.id;

  @override
  DateTime? get createdAt => entity.createdAt;
  @override
  set createdAt(DateTime? value) => entity.createdAt = value;

  @override
  DateTime? get updatedAt => entity.updatedAt;
  @override
  set updatedAt(DateTime? value) => entity.updatedAt = value;

  // ==================== 基本属性 ====================

  String? get title => entity.title;
  set title(String? value) => entity.title = value;

  String? get aiTitle => entity.aiTitle;
  set aiTitle(String? value) => entity.aiTitle = value;

  String? get content => entity.content;
  set content(String? value) => entity.content = value;

  String? get aiContent => entity.aiContent;
  set aiContent(String? value) => entity.aiContent = value;

  String? get htmlContent => entity.htmlContent;
  set htmlContent(String? value) => entity.htmlContent = value;

  String? get aiMarkdownContent => entity.aiMarkdownContent;
  set aiMarkdownContent(String? value) => entity.aiMarkdownContent = value;

  String? get url => entity.url;
  set url(String? value) => entity.url = value;

  String? get comment => entity.comment;
  set comment(String? value) => entity.comment = value;

  String get status => entity.status;
  set status(String value) => entity.status = value;

  bool get isFavorite => entity.isFavorite;
  set isFavorite(bool value) => entity.isFavorite = value;

  DateTime? get pubDate => entity.pubDate;
  set pubDate(DateTime? value) => entity.pubDate = value;

  String? get coverImage => entity.coverImage;
  set coverImage(String? value) => entity.coverImage = value;

  String? get coverImageUrl => entity.coverImageUrl;
  set coverImageUrl(String? value) => entity.coverImageUrl = value;

  List<Image> get images => entity.images;
  List<Tag> get tags => entity.tags;

  // ==================== 计算属性 ====================

  List<int> get tagIds => tags.map((t) => t.id).toList();
  String get summary => aiContent ?? content ?? '';

  // ==================== 显示方法 ====================

  String showTitle() {
    if (aiTitle != null && aiTitle!.isNotEmpty) {
      return aiTitle!;
    }
    return title ?? '';
  }

  String showContent() {
    if (aiContent != null && aiContent!.isNotEmpty) {
      return aiContent!;
    }
    return content ?? '';
  }

  // ==================== 图片相关方法 ====================

  String getHeaderImagePath() {
    if (entity.coverImage != null && entity.coverImage!.isNotEmpty) {
      return entity.coverImage!;
    }
    return '';
  }

  bool hasHeaderImage() {
    return getHeaderImagePath().isNotEmpty || (entity.coverImageUrl != null && entity.coverImageUrl!.isNotEmpty);
  }

  bool shouldShowHeaderImage() {
    final path = getHeaderImagePath();
    return (path.isNotEmpty && !path.endsWith('.svg')) ||
        (entity.coverImageUrl != null && entity.coverImageUrl!.isNotEmpty);
  }

  // ==================== 状态管理方法 ====================

  String getStatus() => status;
  void setStatus(String newStatus) => status = newStatus;

  Future<bool> toggleFavorite() async {
    return await ArticleRepository.d.toggleFavorite(id);
  }

  // ==================== 数据操作方法 ====================

  Future<void> save() async {
    await ArticleRepository.d.updateModel(this);
  }

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
