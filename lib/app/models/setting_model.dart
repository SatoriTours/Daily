import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';

/// 设置数据模型类
///
/// 封装Setting实体类，提供属性访问方法
class SettingModel {
  /// 底层实体对象
  final Setting _entity;

  /// 构造函数
  SettingModel(this._entity);

  /// 从ID创建实例
  factory SettingModel.fromId(int id) {
    final setting = SettingRepository.instance.findModel(id);
    if (setting == null) {
      throw Exception('找不到ID为$id的设置');
    }
    return setting;
  }

  /// 从键创建实例
  factory SettingModel.fromKey(String key) {
    final setting = SettingRepository.instance.findByKey(key);
    if (setting == null) {
      throw Exception('找不到键为$key的设置');
    }
    return setting;
  }

  /// 获取底层实体
  Setting get entity => _entity;

  /// ID
  int get id => _entity.id;

  /// 键
  String? get key => _entity.key;
  set key(String? value) => _entity.key = value;

  /// 值
  String? get value => _entity.value;
  set value(String? val) => _entity.value = val;

  /// 保存模型
  Future<void> save() async {
    SettingRepository.instance.save(_entity);
  }
}
