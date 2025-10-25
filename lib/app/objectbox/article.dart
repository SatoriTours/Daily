import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/tag.dart';

@Entity()
class Article implements BaseEntity {
  @override
  @Id()
  int id = 0;
  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;
  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

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
  String status = 'pending';
  String? coverImage;
  String? coverImageUrl;

  @Property(type: PropertyType.date)
  DateTime? pubDate;

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
    this.coverImageUrl,
    this.pubDate,
    DateTime? updatedAt,
    DateTime? createdAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
