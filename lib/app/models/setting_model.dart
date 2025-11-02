import 'package:daily_satori/app/models/base/entity_model.dart';
import 'package:daily_satori/app/objectbox/setting.dart';

/// 设置数据模型类
class SettingModel extends EntityModel<Setting> {
  SettingModel(super.entity);

  String? get key => entity.key;
  set key(String? value) => entity.key = value;

  String? get value => entity.value;
  set value(String? val) => entity.value = val;
}
