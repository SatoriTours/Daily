import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';

/// 文章数据模型类
///
/// 封装Article实体类，提供属性访问方法
class ArticleModel {
  /// 底层实体对象
  final Article _entity;

  /// 构造函数
  ArticleModel(this._entity);

  /// 从ID创建实例
  factory ArticleModel.fromId(int id) {
    final article = ArticleRepository.find(id);
    if (article == null) {
      throw Exception('找不到ID为$id的文章');
    }
    return article;
  }

  /// 获取底层实体
  Article get entity => _entity;

  /// ID
  int get id => _entity.id;

  /// 标题
  String? get title => _entity.title;
  set title(String? value) => _entity.title = value;

  /// AI生成的标题
  String? get aiTitle => _entity.aiTitle;
  set aiTitle(String? value) => _entity.aiTitle = value;

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

  /// 内容
  String? get content => _entity.content;
  set content(String? value) => _entity.content = value;

  /// AI生成的内容
  String? get aiContent => _entity.aiContent;
  set aiContent(String? value) => _entity.aiContent = value;

  /// HTML格式的内容
  String? get htmlContent => _entity.htmlContent;
  set htmlContent(String? value) => _entity.htmlContent = value;

  /// AI生成的Markdown内容
  String? get aiMarkdownContent => _entity.aiMarkdownContent;
  set aiMarkdownContent(String? value) => _entity.aiMarkdownContent = value;

  /// URL
  String? get url => _entity.url;
  set url(String? value) => _entity.url = value;

  /// 是否收藏
  bool get isFavorite => _entity.isFavorite;
  set isFavorite(bool value) => _entity.isFavorite = value;

  /// 评论
  String? get comment => _entity.comment;
  set comment(String? value) => _entity.comment = value;

  /// 处理状态
  String get status => _entity.status;
  set status(String value) => _entity.status = value;

  /// 封面图片路径
  String? get coverImage => _entity.coverImage;
  set coverImage(String? value) => _entity.coverImage = value;

  /// 封面图片URL
  String? get coverImageUrl => _entity.coverImageUrl;
  set coverImageUrl(String? value) => _entity.coverImageUrl = value;

  /// 发布日期
  DateTime? get pubDate => _entity.pubDate;
  set pubDate(DateTime? value) => _entity.pubDate = value;

  /// 更新日期
  DateTime? get updatedAt => _entity.updatedAt;
  set updatedAt(DateTime? value) => _entity.updatedAt = value;

  /// 创建日期
  DateTime? get createdAt => _entity.createdAt;
  set createdAt(DateTime? value) => _entity.createdAt = value;

  /// 图片列表
  List<Image> get images => _entity.images;

  /// 截图列表
  List<Screenshot> get screenshots => _entity.screenshots;

  /// 标签列表
  List<Tag> get tags => _entity.tags;

  /// 获取主图路径
  String getHeaderImagePath() {
    if (_entity.coverImage != null && _entity.coverImage!.isNotEmpty) {
      return _entity.coverImage!;
    }
    return '';
  }

  /// 检查是否有主图
  bool hasHeaderImage() {
    return getHeaderImagePath().isNotEmpty || (_entity.coverImageUrl != null && _entity.coverImageUrl!.isNotEmpty);
  }

  /// 检查是否应该显示头部图片
  bool shouldShowHeaderImage() {
    final path = getHeaderImagePath();
    return (path.isNotEmpty && !path.endsWith('.svg')) ||
        (_entity.coverImageUrl != null && _entity.coverImageUrl!.isNotEmpty);
  }

  /// 切换收藏状态
  Future<bool> toggleFavorite() async {
    return await ArticleRepository.toggleFavorite(id);
  }

  /// 获取处理状态
  String getStatus() {
    return status;
  }

  /// 设置处理状态
  void setStatus(String newStatus) {
    status = newStatus;
  }

  /// 保存模型
  Future<void> save() async {
    await ArticleRepository.update(this);
  }
}
