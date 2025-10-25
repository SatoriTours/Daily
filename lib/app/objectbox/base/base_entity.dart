/// ObjectBox 实体接口
///
/// 由于 ObjectBox 代码生成器的限制，无法识别继承或 mixin 中的字段
/// 因此这个接口主要用于类型约束和公共方法声明
///
/// 所有 ObjectBox 实体类必须实现此接口，并声明以下字段：
/// ```dart
/// @Entity()
/// class YourEntity implements BaseEntity {
///   @Id() int id = 0;
///   @Property(type: PropertyType.date) late DateTime createdAt;
///   @Property(type: PropertyType.date) late DateTime updatedAt;
///   // ... 其他字段
/// }
/// ```
abstract interface class BaseEntity {
  int get id;
  DateTime get createdAt;
  DateTime get updatedAt;
}
