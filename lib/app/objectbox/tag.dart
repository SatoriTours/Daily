import 'package:daily_satori/app/objectbox/article.dart';
import 'package:objectbox/objectbox.dart';

@Entity()
class Tag {
  @Id()
  int id = 0;
  String? name;
  String? icon;

  final articles = ToMany<Article>();

  Tag({this.id = 0, this.name, this.icon});
}
