/// Entity 模型 Mixin
///
/// 提供所有实体模型的公共接口,包括实体访问和时间戳管理
mixin EntityModelMixin<T> {
  /// 获取底层实体对象
  T get entity;

  /// 获取实体ID
  int get id;

  /// 创建时间
  DateTime? get createdAt;
  set createdAt(DateTime? value);

  /// 更新时间
  DateTime? get updatedAt;
  set updatedAt(DateTime? value);
}
