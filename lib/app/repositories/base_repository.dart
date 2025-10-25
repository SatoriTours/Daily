import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// Repository 基类
///
/// 提供通用的数据库操作方法的抽象基类
/// 子类可以覆盖 pageSize,box 已提供默认实现(使用缓存优化性能)
///
/// 泛型参数:
/// - E: 实体类型(必须实现 BaseEntity 接口)
/// - M: Model 类型(必须继承 EntityModel\<E\>)
abstract class BaseRepository<E extends BaseEntity, M extends EntityModel<E>> {
  /// 获取 ObjectBox Box 实例
  /// 使用 late final 实现延迟初始化和结果缓存，优化性能
  late final Box<E> box = ObjectboxService.i.box<E>();

  /// 每页数据量（子类可以覆盖，默认 20）
  int get pageSize => 20;

  /// 将实体转换为 Model(子类必须实现)
  M toModel(E entity);

  /// 从 Model 获取实体(提供默认实现)
  E toEntity(M model) {
    return model.entity;
  }

  // ==================== 基础 CRUD 操作 ====================

  /// 查询所有 Model
  List<M> all() {
    return box.getAll().map(toModel).toList();
  }

  /// 根据 ID 查找 Model
  M? find(int id) {
    final entity = box.get(id);
    return entity != null ? toModel(entity) : null;
  }

  /// 保存 Model
  Future<int> save(M model) async {
    try {
      final id = await box.putAsync(toEntity(model));
      return id;
    } catch (e) {
      rethrow;
    }
  }

  /// 批量保存 Model
  Future<List<int>> saveMany(List<M> models) async {
    try {
      final entities = models.map(toEntity).toList();
      return await box.putManyAsync(entities);
    } catch (e) {
      rethrow;
    }
  }

  /// 删除 Model
  bool remove(int id) {
    return box.remove(id);
  }

  /// 批量删除 Model
  int removeMany(List<int> ids) {
    return box.removeMany(ids);
  }

  /// 获取 Model 总数
  int count() {
    return box.count();
  }

  /// 获取总页数
  int totalPages() {
    final total = count();
    return (total / pageSize).ceil();
  }

  /// 清空所有数据
  int removeAll() {
    return box.removeAll();
  }

  // ==================== 方法别名(兼容旧代码) ====================

  /// 查询所有 Model(别名)
  List<M> allModels() => all();

  /// 根据 ID 查找 Model(别名)
  M? findModel(int id) => find(id);

  /// 保存 Model(别名)
  Future<int> saveModel(M model) => save(model);

  /// 更新 Model(别名,实际与 save 一样)
  Future<int> updateModel(M model) => save(model);

  /// 批量保存 Model(别名)
  Future<List<int>> saveModels(List<M> models) => saveMany(models);

  // ==================== 分页查询 ====================

  /// 分页查询所有 Model
  List<M> allPaginated({required int page, QueryIntegerProperty<E>? orderBy, bool descending = false}) {
    final offset = (page - 1) * pageSize;
    final queryBuilder = box.query();

    if (orderBy != null) {
      queryBuilder.order(orderBy, flags: descending ? Order.descending : 0);
    }

    final query = queryBuilder.build();
    try {
      query
        ..offset = offset
        ..limit = pageSize;
      return query.find().map(toModel).toList();
    } finally {
      query.close();
    }
  }

  /// 分页查询所有 Model(别名)
  List<M> allModelsPaginated({required int page, QueryIntegerProperty<E>? orderBy, bool descending = false}) {
    return allPaginated(page: page, orderBy: orderBy, descending: descending);
  }

  // ==================== 条件查询 ====================

  /// 根据条件查询 Model 列表
  List<M> findByCondition(Condition<E> condition) {
    final query = box.query(condition).build();
    try {
      return query.find().map(toModel).toList();
    } finally {
      query.close();
    }
  }

  /// 根据条件查询第一个 Model
  M? findFirstByCondition(Condition<E> condition) {
    final query = box.query(condition).build();
    try {
      final entity = query.findFirst();
      return entity != null ? toModel(entity) : null;
    } finally {
      query.close();
    }
  }

  /// 根据条件统计数量
  int countByCondition(Condition<E> condition) {
    final query = box.query(condition).build();
    try {
      return query.count();
    } finally {
      query.close();
    }
  }

  /// 根据条件分页查询 Model
  List<M> findByConditionPaginated({
    required Condition<E> condition,
    required int page,
    QueryIntegerProperty<E>? orderBy,
    bool descending = false,
  }) {
    final offset = (page - 1) * pageSize;
    final queryBuilder = box.query(condition);

    if (orderBy != null) {
      queryBuilder.order(orderBy, flags: descending ? Order.descending : 0);
    }

    final query = queryBuilder.build();
    try {
      query
        ..offset = offset
        ..limit = pageSize;
      return query.find().map(toModel).toList();
    } finally {
      query.close();
    }
  }

  /// 根据条件查询 Model 列表(别名)
  List<M> findModelsByCondition(Condition<E> condition) => findByCondition(condition);

  /// 根据条件分页查询 Model(别名)
  List<M> findModelsByConditionPaginated({
    required Condition<E> condition,
    required int page,
    QueryIntegerProperty<E>? orderBy,
    bool descending = false,
  }) {
    return findByConditionPaginated(condition: condition, page: page, orderBy: orderBy, descending: descending);
  }

  // ==================== 字符串查询 ====================

  /// 根据字符串字段模糊搜索
  List<M> searchByString({
    required QueryStringProperty<E> property,
    required String searchText,
    bool caseSensitive = false,
  }) {
    final condition = property.contains(searchText, caseSensitive: caseSensitive);
    return findByCondition(condition);
  }

  /// 根据字符串字段精确匹配
  List<M> findByStringEquals(QueryStringProperty<E> property, String value) {
    final condition = property.equals(value);
    return findByCondition(condition);
  }

  /// 根据字符串字段精确匹配，返回第一个结果
  M? findFirstByStringEquals(QueryStringProperty<E> property, String value) {
    final condition = property.equals(value);
    return findFirstByCondition(condition);
  }

  // ==================== 日期查询 ====================

  /// 根据日期范围查询
  List<M> findByDateRange({
    required QueryIntegerProperty<E> property,
    required DateTime startDate,
    required DateTime endDate,
  }) {
    final condition = property.between(startDate.millisecondsSinceEpoch, endDate.millisecondsSinceEpoch);
    return findByCondition(condition);
  }

  /// 根据指定日期查询
  List<M> findByDate({required QueryIntegerProperty<E> property, required DateTime date}) {
    final startOfDay = DateTime(date.year, date.month, date.day);
    final endOfDay = startOfDay.add(const Duration(days: 1));
    return findByDateRange(property: property, startDate: startOfDay, endDate: endOfDay);
  }

  // ==================== 统计功能 ====================

  /// 获取每天 Model 数量统计(通用实现)
  /// 需要子类传入日期字段属性
  Map<DateTime, int> getDailyCounts(QueryIntegerProperty<E> dateProperty) {
    final counts = <DateTime, int>{};
    final allModelsData = all();

    for (final model in allModelsData) {
      final date = model.entity.createdAt;
      final dateKey = DateTime(date.year, date.month, date.day);
      counts[dateKey] = (counts[dateKey] ?? 0) + 1;
    }

    return counts;
  }
}
