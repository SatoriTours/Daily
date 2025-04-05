import 'package:objectbox/objectbox.dart';

@Entity()
class AIConfig {
  @Id()
  int id = 0;

  /// 配置名称
  String name;

  /// API地址
  String apiAddress;

  /// API令牌
  String apiToken;

  /// 模型名称
  String modelName;

  /// 功能类型
  /// 1: 文章分析和Markdown转换
  /// 2: 书本解读
  /// 3: 日记总结
  /// 0: 通用配置，可被继承
  int functionType;

  /// 是否继承自通用配置
  bool inheritFromGeneral;

  /// 是否为默认配置
  bool isDefault;

  /// 创建时间
  @Property(type: PropertyType.date)
  DateTime createdAt;

  /// 更新时间
  @Property(type: PropertyType.date)
  DateTime updatedAt;

  /// 构造函数
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
  }) : createdAt = createdAt ?? DateTime.now(),
       updatedAt = updatedAt ?? DateTime.now();
}
