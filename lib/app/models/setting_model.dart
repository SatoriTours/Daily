import 'package:daily_satori/app/models/mixins/entity_model_mixin.dart';
import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';

/// 设置数据模型类
class SettingModel with EntityModelMixin<Setting> {
  final Setting _entity;

  SettingModel(this._entity);

  factory SettingModel.fromId(int id) {
    final setting = SettingRepository.instance.findModel(id);
    if (setting == null) {
      throw Exception('找不到ID为$id的设置');
    }
    return setting;
  }

  factory SettingModel.fromKey(String key) {
    final setting = SettingRepository.instance.findByKey(key);
    if (setting == null) {
      throw Exception('找不到键为$key的设置');
    }
    return setting;
  }

  @override
  Setting get entity => _entity;

  @override
  int get id => entity.id;

  @override
  DateTime? get createdAt => null;
  @override
  set createdAt(DateTime? value) {}

  @override
  DateTime? get updatedAt => null;
  @override
  set updatedAt(DateTime? value) {}

  String? get key => entity.key;
  set key(String? value) => entity.key = value;

  String? get value => entity.value;
  set value(String? val) => entity.value = val;

  Future<void> save() async {
    SettingRepository.instance.save(entity);
  }
}
