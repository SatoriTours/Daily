import 'package:objectbox/objectbox.dart';

import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/tag.dart';

@Entity()
class Article {
  @Id()
  int id = 0;

  String? title;
  String? aiTitle;
  String? content;
  String? aiContent;
  String? htmlContent;
  String? aiMarkdownContent;

  @Unique()
  String? url;
  bool isFavorite = false;
  String? comment;

  /// 处理状态: pending, web_content_fetched, completed, error
  String status = 'pending';

  /// 封面图片路径 和 URL
  String? coverImage;
  String? coverImageUrl;

  @Property(type: PropertyType.date)
  DateTime? pubDate;

  @Property(type: PropertyType.date)
  DateTime? updatedAt;

  @Property(type: PropertyType.date)
  DateTime? createdAt;

  @Backlink()
  final images = ToMany<Image>();

  final tags = ToMany<Tag>();

  Article({
    this.id = 0,
    this.title,
    this.aiTitle,
    this.content,
    this.aiContent,
    this.htmlContent,
    this.aiMarkdownContent,
    this.url,
    this.isFavorite = false,
    this.comment,
    this.status = 'pending',
    this.coverImage,
    this.pubDate,
    this.updatedAt,
    this.createdAt,
  });
}
