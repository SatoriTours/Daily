import 'dart:convert';
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

  /// 额外数据缓存
  Map<String, dynamic>? _extraDataCache;

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

  /// 内容
  String? get content => _entity.content;
  set content(String? value) => _entity.content = value;

  /// AI生成的内容
  String? get aiContent => _entity.aiContent;
  set aiContent(String? value) => _entity.aiContent = value;

  /// HTML格式的内容
  String? get htmlContent => _entity.htmlContent;
  set htmlContent(String? value) => _entity.htmlContent = value;

  /// URL
  String? get url => _entity.url;
  set url(String? value) => _entity.url = value;

  /// 是否收藏
  bool get isFavorite => _entity.isFavorite;
  set isFavorite(bool value) => _entity.isFavorite = value;

  /// 评论
  String? get comment => _entity.comment;
  set comment(String? value) => _entity.comment = value;

  /// 额外数据JSON
  String? get extraData => _entity.extraData;
  set extraData(String? value) => _entity.extraData = value;

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

  /// 获取额外数据
  Map<String, dynamic> getExtraDataMap() {
    if (_extraDataCache != null) {
      return _extraDataCache!;
    }

    if (extraData == null || extraData!.isEmpty) {
      _extraDataCache = {};
      return {};
    }

    try {
      _extraDataCache = json.decode(extraData!) as Map<String, dynamic>;
      return _extraDataCache!;
    } catch (e) {
      return {};
    }
  }

  /// 设置额外数据
  void setExtraData(String key, dynamic value) {
    final data = getExtraDataMap();
    data[key] = value;
    _extraDataCache = data;
    extraData = json.encode(data);
  }

  /// 获取额外数据
  dynamic getExtraData(String key) {
    return getExtraDataMap()[key];
  }

  /// 获取主图路径
  String getHeaderImagePath() {
    return images.isEmpty ? '' : (images.first.path ?? '');
  }

  /// 检查是否有主图
  bool hasHeaderImage() {
    return getHeaderImagePath().isNotEmpty;
  }

  /// 检查是否应该显示头部图片
  bool shouldShowHeaderImage() {
    final path = getHeaderImagePath();
    return path.isNotEmpty && !path.endsWith('.svg');
  }

  /// 切换收藏状态
  Future<bool> toggleFavorite() async {
    return await ArticleRepository.toggleFavorite(id);
  }

  /// 获取处理状态
  String getStatus() {
    return getExtraData('status') ?? 'pending';
  }

  /// 设置处理状态
  void setStatus(String status) {
    setExtraData('status', status);
  }

  /// 保存模型
  Future<void> save() async {
    await ArticleRepository.update(this);
  }
}
