import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 基础模型类
///
/// 采用Rails风格的Model设计，为所有模型提供通用功能
/// 使用泛型T表示对应的实体类型
abstract class BaseModel<T> {
  // ObjectBox服务
  final _objectboxService = ObjectboxService.i;

  // 获取实体对应的Box
  Box<T> get box => _objectboxService.box<T>();

  // 实体对象
  T? _entity;

  /// 获取原始实体
  T? get entity => _entity;

  /// 构造函数，接收一个实体
  BaseModel.withEntity(this._entity);

  /// ID属性 - 子类必须实现
  int get id;

  /// 创建模型实例的工厂方法 - 子类必须实现
  static BaseModel fromEntity(dynamic entity) {
    throw UnimplementedError('Subclasses must implement fromEntity');
  }

  /// 保存实体方法 - 子类必须实现
  Future<int> _saveEntity(T entity);

  /// 实例方法 - 保存当前模型
  Future<int> save() async {
    if (_entity == null) return 0;
    return await _saveEntity(_entity!);
  }

  /// 查找所有记录
  List<BaseModel<T>> findAll() {
    final entities = box.getAll();
    return _fromEntityList(entities);
  }

  /// 根据ID查找记录
  BaseModel<T>? findById(int id) {
    final entity = box.get(id);
    return entity != null ? _createFromEntity(entity) : null;
  }

  /// 保存记录
  Future<int> saveModel(BaseModel<T> model) async {
    if (model._entity == null) return 0;
    return await _saveEntity(model._entity!);
  }

  /// 删除记录
  bool deleteById(int id) {
    return box.remove(id);
  }

  /// 实例方法 - 删除当前模型
  bool delete() {
    if (_entity == null) return false;
    return deleteById(id);
  }

  /// 将单个实体转换为模型
  BaseModel<T> _createFromEntity(T entity);

  /// 将实体列表转换为模型列表
  List<BaseModel<T>> _fromEntityList(List<T> entities) {
    return entities.map((entity) => _createFromEntity(entity)).toList();
  }
}
