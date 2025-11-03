import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/models/article_status.dart';

/// 文章数据模型类
class ArticleModel extends EntityModel<Article> {
  ArticleModel(super.entity);

  // ==================== 基本属性 ====================

  String? get title => entity.title;
  set title(String? value) => entity.title = value;

  String? get singleLineTitle => title?.replaceAll('\n', ' ').replaceAll(RegExp(r'\s+'), ' ').trim();

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

  /// 获取文章状态（枚举类型）
  ArticleStatus get status => ArticleStatus.fromValue(entity.status);

  /// 设置文章状态（枚举类型）
  set status(ArticleStatus value) => entity.status = value.value;

  /// 获取原始状态值（字符串，用于数据库操作）
  String get statusValue => entity.status;

  /// 设置原始状态值（字符串，用于数据库操作）
  set statusValue(String value) => entity.status = value;

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

  ArticleStatus getStatus() => status;
  void setStatus(ArticleStatus newStatus) => status = newStatus;
}
