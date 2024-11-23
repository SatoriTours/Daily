import 'package:objectbox/objectbox.dart';

@Entity()
class Setting {
  @Id()
  int id = 0;

  @Unique()
  String? key;
  String? value;

  Setting({this.id = 0, this.key, this.value});
}
