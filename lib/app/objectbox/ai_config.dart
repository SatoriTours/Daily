import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

@Entity()
class AIConfig implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  String name;
  String apiAddress;
  String apiToken;
  String modelName;

  /// 功能类型: 0=通用配置, 1=文章处理, 2=日记处理, 3=翻译, 4=其他
  int functionType;

  /// 是否继承通用配置
  bool inheritFromGeneral;

  /// 是否为默认配置
  bool isDefault;

  AIConfig({
    this.id = 0,
    required this.name,
    required this.apiAddress,
    required this.apiToken,
    required this.modelName,
    required this.functionType,
    this.inheritFromGeneral = false,
    this.isDefault = false,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    this.updatedAt = updatedAt ?? DateTime.now();
  }
}
