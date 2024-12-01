import 'package:objectbox/objectbox.dart';

import 'package:daily_satori/app/objectbox/article.dart';

@Entity()
class Image {
  @Id()
  int id = 0;
  String? url;
  String? path;

  final article = ToOne<Article>();

  Image({this.id = 0, this.url, this.path});
}
