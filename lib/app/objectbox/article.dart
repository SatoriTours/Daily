import 'package:objectbox/objectbox.dart';

import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
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

  @Unique()
  String? url;
  bool isFavorite = false;
  String? comment;

  @Property(type: PropertyType.date)
  DateTime? pubDate;

  @Property(type: PropertyType.date)
  DateTime? updatedAt;

  @Property(type: PropertyType.date)
  DateTime? createdAt;

  @Backlink()
  final images = ToMany<Image>();

  @Backlink()
  final screenshots = ToMany<Screenshot>();

  final tags = ToMany<Tag>();

  Article({
    this.id = 0,
    this.title,
    this.aiTitle,
    this.content,
    this.aiContent,
    this.htmlContent,
    this.url,
    this.isFavorite = false,
    this.comment,
    this.pubDate,
    this.updatedAt,
    this.createdAt,
  });
}
