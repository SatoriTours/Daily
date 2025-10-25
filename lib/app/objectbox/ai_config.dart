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
  int functionType;
  bool inheritFromGeneral;
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
