/// 基础模型类
///
/// 为所有模型类提供公共功能，封装ObjectBox实体访问模式
abstract class BaseModel<T> {
  /// 底层实体对象
  final T _entity;

  /// 构造函数
  BaseModel(this._entity);

  /// 获取底层实体
  T get entity => _entity;

  /// ID getter - 子类必须实现
  int get id;

  /// 创建时间 - 子类必须实现
  DateTime? get createdAt;
  set createdAt(DateTime? value);

  /// 更新时间 - 子类必须实现
  DateTime? get updatedAt;
  set updatedAt(DateTime? value);
}
