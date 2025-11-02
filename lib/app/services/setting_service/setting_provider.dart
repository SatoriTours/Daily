import 'package:flutter_settings_screens/flutter_settings_screens.dart';
import 'package:daily_satori/app/repositories/repositories.dart';

class SettingProvider extends CacheProvider {
  String _value(String key) {
    return SettingRepository.i.getValue(key, defaultValue: '') ?? '';
  }

  @override
  bool containsKey(String key) {
    return SettingRepository.i.containsKey(key);
  }

  @override
  bool? getBool(String key, {bool? defaultValue}) {
    if (containsKey(key)) {
      if (_value(key).toLowerCase() == 'true') {
        return true;
      }
      return false;
    }
    return defaultValue;
  }

  @override
  double? getDouble(String key, {double? defaultValue}) {
    try {
      if (containsKey(key)) {
        double.parse(_value(key));
      }
    } catch (e) {
      // 解析失败时使用默认值
    }

    return defaultValue;
  }

  @override
  int? getInt(String key, {int? defaultValue}) {
    try {
      if (containsKey(key)) {
        int.parse(_value(key));
      }
    } catch (e) {
      // 解析失败时使用默认值
    }

    return defaultValue;
  }

  @override
  Set getKeys() {
    return SettingRepository.i.getKeys();
  }

  @override
  String? getString(String key, {String? defaultValue}) {
    return SettingRepository.i.getValue(key, defaultValue: defaultValue);
  }

  @override
  T? getValue<T>(String key, {T? defaultValue}) {
    if (T == bool) {
      return getBool(key, defaultValue: defaultValue as bool?) as T?;
    } else if (T == double) {
      return getDouble(key, defaultValue: defaultValue as double?) as T?;
    } else if (T == int) {
      return getInt(key, defaultValue: defaultValue as int?) as T?;
    } else if (T == String) {
      return getString(key, defaultValue: defaultValue as String?) as T?;
    }
    return defaultValue; // 如果 T 不是以上类型，返回默认值
  }

  @override
  Future<void> init() async {}

  @override
  Future<void> remove(String key) async {
    SettingRepository.i.removeByKey(key);
  }

  @override
  Future<void> removeAll() async {
    SettingRepository.i.removeAll();
  }

  @override
  Future<void> setBool(String key, bool? value) async {
    if (value == true) {
      SettingRepository.i.setValue(key, 'true');
    } else {
      SettingRepository.i.setValue(key, 'false');
    }
  }

  @override
  Future<void> setDouble(String key, double? value) async {
    if (value == null) {
      SettingRepository.i.setValue(key, '0.0');
    } else {
      SettingRepository.i.setValue(key, value.toString());
    }
  }

  @override
  Future<void> setInt(String key, int? value) async {
    if (value == null) {
      SettingRepository.i.setValue(key, '0');
    } else {
      SettingRepository.i.setValue(key, value.toString());
    }
  }

  @override
  Future<void> setObject<T>(String key, T? value) async {
    if (value is bool) {
      await setBool(key, value);
    } else if (value is double) {
      await setDouble(key, value);
    } else if (value is int) {
      await setInt(key, value);
    } else if (value is String) {
      await setString(key, value);
    } else {
      throw UnimplementedError('不支持的对象类型：${T.toString()}');
    }
  }

  @override
  Future<void> setString(String key, String? value) async {
    if (value == null) {
      SettingRepository.i.setValue(key, '');
    } else {
      SettingRepository.i.setValue(key, value);
    }
  }
}
