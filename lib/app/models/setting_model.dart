import 'package:daily_satori/app/models/base_model.dart';
import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';

/// 设置数据模型类
///
/// 封装Setting实体类，提供属性访问方法
class SettingModel extends BaseModel<Setting> {
  /// 构造函数
  SettingModel(super.entity);

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

  /// ID
  @override
  int get id => entity.id;

  /// 键
  String? get key => entity.key;
  set key(String? value) => entity.key = value;

  /// 值
  String? get value => entity.value;
  set value(String? val) => entity.value = val;

  /// 创建时间 - Setting实体不包含此字段
  @override
  DateTime? get createdAt => null;
  @override
  set createdAt(DateTime? value) {}

  /// 更新时间 - Setting实体不包含此字段
  @override
  DateTime? get updatedAt => null;
  @override
  set updatedAt(DateTime? value) {}

  /// 保存模型
  Future<void> save() async {
    SettingRepository.instance.save(entity);
  }
}
