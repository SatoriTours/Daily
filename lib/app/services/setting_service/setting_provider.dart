import 'package:flutter_settings_screens/flutter_settings_screens.dart';
import 'package:daily_satori/app/data/data.dart';

class SettingProvider extends CacheProvider {
  String _value(String key) => SettingRepository.i.getValue(key, defaultValue: '') ?? '';

  @override
  bool containsKey(String key) => SettingRepository.i.containsKey(key);

  @override
  bool? getBool(String key, {bool? defaultValue}) {
    if (!containsKey(key)) return defaultValue;
    return _value(key).toLowerCase() == 'true';
  }

  @override
  double? getDouble(String key, {double? defaultValue}) {
    if (!containsKey(key)) return defaultValue;
    try {
      return double.parse(_value(key));
    } catch (_) {
      return defaultValue;
    }
  }

  @override
  int? getInt(String key, {int? defaultValue}) {
    if (!containsKey(key)) return defaultValue;
    try {
      return int.parse(_value(key));
    } catch (_) {
      return defaultValue;
    }
  }

  @override
  Set getKeys() => SettingRepository.i.getKeys();

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
    return defaultValue;
  }

  @override
  Future<void> init() async {}

  @override
  Future<void> remove(String key) async => SettingRepository.i.removeByKey(key);

  @override
  Future<void> removeAll() async => SettingRepository.i.removeAll();

  @override
  Future<void> setBool(String key, bool? value) async {
    SettingRepository.i.setValue(key, value == true ? 'true' : 'false');
  }

  @override
  Future<void> setDouble(String key, double? value) async {
    SettingRepository.i.setValue(key, value?.toString() ?? '0.0');
  }

  @override
  Future<void> setInt(String key, int? value) async {
    SettingRepository.i.setValue(key, value?.toString() ?? '0');
  }

  @override
  Future<void> setObject<T>(String key, T? value) async {
    if (value == null) {
      await setString(key, null);
    } else if (value is bool) {
      await setBool(key, value);
    } else if (value is double) {
      await setDouble(key, value);
    } else if (value is int) {
      await setInt(key, value);
    } else if (value is String) {
      await setString(key, value);
    } else {
      throw UnimplementedError('不支持的对象类型：$T');
    }
  }

  @override
  Future<void> setString(String key, String? value) async {
    SettingRepository.i.setValue(key, value ?? '');
  }
}
