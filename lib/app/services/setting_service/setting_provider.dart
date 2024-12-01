import 'package:flutter_settings_screens/flutter_settings_screens.dart';

import 'package:daily_satori/app/services/setting_service/setting_service.dart';

class SettingProvider extends CacheProvider {
  final _service = SettingService.i;

  String _value(String key) {
    return _service.getSetting(key);
  }

  @override
  bool containsKey(String key) {
    return _service.containsKey(key);
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
    } catch (e) {}

    return defaultValue;
  }

  @override
  int? getInt(String key, {int? defaultValue}) {
    try {
      if (containsKey(key)) {
        int.parse(_value(key));
      }
    } catch (e) {}

    return defaultValue;
  }

  @override
  Set getKeys() {
    return _service.getKeys();
  }

  @override
  String? getString(String key, {String? defaultValue}) {
    return _service.getSetting(key, defaultValue: defaultValue);
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
    await _service.remove(key);
  }

  @override
  Future<void> removeAll() async {
    await _service.removeAll();
  }

  @override
  Future<void> setBool(String key, bool? value) async {
    if (value == true) {
      await _service.saveSetting(key, 'true');
    } else {
      await _service.saveSetting(key, 'false');
    }
  }

  @override
  Future<void> setDouble(String key, double? value) async {
    if (value == null) {
      await _service.saveSetting(key, '0.0');
    } else {
      await _service.saveSetting(key, value.toString());
    }
  }

  @override
  Future<void> setInt(String key, int? value) async {
    if (value == null) {
      await _service.saveSetting(key, '0');
    } else {
      await _service.saveSetting(key, value.toString());
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
      await _service.saveSetting(key, '');
    } else {
      await _service.saveSetting(key, value);
    }
  }
}
