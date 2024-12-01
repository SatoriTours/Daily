import 'package:objectbox/objectbox.dart';

import 'package:daily_satori/app/objectbox/article.dart';

@Entity()
class Screenshot {
  @Id()
  int id = 0;
  String? path;

  final article = ToOne<Article>();

  Screenshot({this.id = 0, this.path});
}
