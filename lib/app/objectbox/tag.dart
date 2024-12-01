import 'package:objectbox/objectbox.dart';

import 'package:daily_satori/app/objectbox/article.dart';

@Entity()
class Tag {
  @Id()
  int id = 0;

  @Unique()
  String? name;
  String? icon;

  @Backlink()
  final articles = ToMany<Article>();

  Tag({this.id = 0, this.name, this.icon});
}
