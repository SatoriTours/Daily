import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// Repository 基类
///
/// 提供通用的数据库操作方法的抽象基类
/// 子类可以覆盖 pageSize，box 已提供默认实现（使用缓存优化性能）
///
/// 泛型参数：
/// - T: 实体类型
/// - M: Model 类型（用于业务逻辑层）
abstract class BaseRepository<T, M> {
  /// 获取 ObjectBox Box 实例
  /// 使用 late final 实现延迟初始化和结果缓存，优化性能
  late final Box<T> box = ObjectboxService.i.box<T>();

  /// 每页数据量（子类可以覆盖，默认 20）
  int get pageSize => 20;

  /// 将实体转换为 Model（子类必须实现）
  M toModel(T entity);

  /// 从 Model 获取实体（子类必须实现）
  T toEntity(M model);

  // ==================== 基础 CRUD 操作 ====================

  /// 查询所有实体
  List<T> all() {
    return box.getAll();
  }

  /// 根据 ID 查找实体
  T? find(int id) {
    return box.get(id);
  }

  /// 保存实体（创建或更新）
  int save(T entity) {
    return box.put(entity);
  }

  /// 批量保存实体
  List<int> saveMany(List<T> entities) {
    return box.putMany(entities);
  }

  /// 删除实体
  bool remove(int id) {
    return box.remove(id);
  }

  /// 批量删除实体
  int removeMany(List<int> ids) {
    return box.removeMany(ids);
  }

  /// 获取实体总数
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

  // ==================== 分页查询 ====================

  /// 分页查询所有实体
  List<T> allPaginated({required int page, QueryIntegerProperty<T>? orderBy, bool descending = false}) {
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
      return query.find();
    } finally {
      query.close();
    }
  }

  // ==================== 条件查询 ====================

  /// 根据条件查询实体列表
  List<T> findByCondition(Condition<T> condition) {
    final query = box.query(condition).build();
    try {
      return query.find();
    } finally {
      query.close();
    }
  }

  /// 根据条件查询第一个实体
  T? findFirstByCondition(Condition<T> condition) {
    final query = box.query(condition).build();
    try {
      return query.findFirst();
    } finally {
      query.close();
    }
  }

  /// 根据条件统计数量
  int countByCondition(Condition<T> condition) {
    final query = box.query(condition).build();
    try {
      return query.count();
    } finally {
      query.close();
    }
  }

  /// 根据条件分页查询
  List<T> findByConditionPaginated({
    required Condition<T> condition,
    required int page,
    QueryIntegerProperty<T>? orderBy,
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
      return query.find();
    } finally {
      query.close();
    }
  }

  // ==================== 字符串查询 ====================

  /// 根据字符串字段模糊搜索
  List<T> searchByString({
    required QueryStringProperty<T> property,
    required String searchText,
    bool caseSensitive = false,
  }) {
    final condition = property.contains(searchText, caseSensitive: caseSensitive);
    return findByCondition(condition);
  }

  /// 根据字符串字段精确匹配
  List<T> findByStringEquals(QueryStringProperty<T> property, String value) {
    final condition = property.equals(value);
    return findByCondition(condition);
  }

  /// 根据字符串字段精确匹配，返回第一个结果
  T? findFirstByStringEquals(QueryStringProperty<T> property, String value) {
    final condition = property.equals(value);
    return findFirstByCondition(condition);
  }

  // ==================== 日期查询 ====================

  /// 根据日期范围查询
  List<T> findByDateRange({
    required QueryIntegerProperty<T> property,
    required DateTime startDate,
    required DateTime endDate,
  }) {
    final condition = property.between(startDate.millisecondsSinceEpoch, endDate.millisecondsSinceEpoch);
    return findByCondition(condition);
  }

  /// 根据指定日期查询
  List<T> findByDate({required QueryIntegerProperty<T> property, required DateTime date}) {
    final startOfDay = DateTime(date.year, date.month, date.day);
    final endOfDay = startOfDay.add(const Duration(days: 1));
    return findByDateRange(property: property, startDate: startOfDay, endDate: endOfDay);
  }

  // ==================== Model 相关操作 ====================

  /// 查询所有实体（返回 Model）
  List<M> allModels() {
    return all().map((e) => toModel(e)).toList();
  }

  /// 根据 ID 查找实体（返回 Model）
  M? findModel(int id) {
    final entity = find(id);
    return entity != null ? toModel(entity) : null;
  }

  /// 保存 Model
  Future<int> saveModel(M model) async {
    try {
      final id = await box.putAsync(toEntity(model));
      return id;
    } catch (e) {
      rethrow;
    }
  }

  /// 更新 Model（实际上与 saveModel 一样，但语义更明确）
  Future<int> updateModel(M model) async {
    return saveModel(model);
  }

  /// 分页查询所有实体（返回 Model）
  List<M> allModelsPaginated({required int page, QueryIntegerProperty<T>? orderBy, bool descending = false}) {
    final entities = allPaginated(page: page, orderBy: orderBy, descending: descending);
    return entities.map((entity) => toModel(entity)).toList();
  }

  /// 根据条件查询（返回 Model）
  List<M> findModelsByCondition(Condition<T> condition) {
    final entities = findByCondition(condition);
    return entities.map((entity) => toModel(entity)).toList();
  }

  /// 根据条件分页查询（返回 Model）
  List<M> findModelsByConditionPaginated({
    required Condition<T> condition,
    required int page,
    QueryIntegerProperty<T>? orderBy,
    bool descending = false,
  }) {
    final entities = findByConditionPaginated(
      condition: condition,
      page: page,
      orderBy: orderBy,
      descending: descending,
    );
    return entities.map((entity) => toModel(entity)).toList();
  }

  /// 获取每天实体数量统计（通用实现）
  /// 需要子类传入日期字段属性
  Map<DateTime, int> getDailyCounts(QueryIntegerProperty<T> dateProperty) {
    final counts = <DateTime, int>{};
    final allModelsData = allModels();

    for (final model in allModelsData) {
      final date = extractDateFromModel(model);
      if (date != null) {
        final dateKey = DateTime(date.year, date.month, date.day);
        counts[dateKey] = (counts[dateKey] ?? 0) + 1;
      }
    }

    return counts;
  }

  /// 从 Model 中提取日期（子类可以覆盖）
  /// 默认返回 null，子类根据需要实现
  DateTime? extractDateFromModel(M model) {
    return null;
  }
}
