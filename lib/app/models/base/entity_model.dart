import 'package:daily_satori/app/objectbox/base/base_entity.dart';

/// Entity 模型基类
///
/// 提供所有实体模型的公共功能，包括实体访问和时间戳管理
abstract class EntityModel<T extends BaseEntity> {
  final T _entity;

  EntityModel(this._entity);

  /// 获取底层实体对象
  T get entity => _entity;

  /// 获取实体ID
  int get id => entity.id;

  /// 创建时间
  DateTime get createdAt => entity.createdAt;
  set createdAt(DateTime? value) => entity.createdAt = value ?? DateTime.now();

  /// 更新时间
  DateTime get updatedAt => entity.updatedAt;
  set updatedAt(DateTime? value) => entity.updatedAt = value ?? DateTime.now();
}
