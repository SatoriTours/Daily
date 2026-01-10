import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:yaml/yaml.dart';

import 'package:daily_satori/app/data/setting/setting_repository.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';

enum SupportedLanguage {
  zh('zh', '中文'),
  en('en', 'English');

  const SupportedLanguage(this.code, this.displayName);
  final String code;
  final String displayName;
}

class I18nService extends AppService {
  static const _languageKey = 'app_language';
  static final I18nService _instance = I18nService._();
  static I18nService get i => _instance;
  I18nService._();

  SupportedLanguage currentLanguage = SupportedLanguage.zh;
  YamlMap? _translations;
  final _warnedKeys = <String>{};
  bool _isInitialized = false;

  @override
  ServicePriority get priority => ServicePriority.critical;

  @override
  Future<void> init() async {
    await _loadSavedLanguage();
    await _loadTranslations();
    _isInitialized = true;
  }

  @override
  Future<void> dispose() async {}

  Future<void> changeLanguageAndRestart(SupportedLanguage language, BuildContext context) async {
    if (currentLanguage == language) return;
    try {
      SettingRepository.i.saveSetting(_languageKey, language.code);
      if (!context.mounted) return;
      logger.i('Language changed to: ${language.displayName}, restarting app...');
      AppNavigation.offAllNamed(Routes.home);
    } catch (e, stackTrace) {
      logger.e('Failed to change language', error: e, stackTrace: stackTrace);
    }
  }

  Future<void> useSystemLanguage() async {
    final context = AppNavigation.navigatorKey.currentContext;
    if (context == null) {
      logger.w('useSystemLanguage: No valid context available');
      return;
    }
    final locale = WidgetsBinding.instance.platformDispatcher.locale;
    final targetLanguage = switch (locale.languageCode) {
      'zh' => SupportedLanguage.zh,
      'en' => SupportedLanguage.en,
      _ => SupportedLanguage.zh,
    };
    await changeLanguageAndRestart(targetLanguage, context);
  }

  Future<void> _loadTranslations() async {
    try {
      final yamlString = await rootBundle.loadString('assets/i18n/${currentLanguage.code}.yaml');
      _translations = loadYaml(yamlString);
    } catch (e, stackTrace) {
      logger.e('Failed to load translations for ${currentLanguage.code}', error: e, stackTrace: stackTrace);
      if (currentLanguage != SupportedLanguage.zh) {
        currentLanguage = SupportedLanguage.zh;
        await _loadTranslations();
      }
    }
  }

  Future<void> _loadSavedLanguage() async {
    try {
      final savedCode = SettingRepository.i.getSetting(_languageKey);
      if (savedCode.isNotEmpty) {
        final saved = SupportedLanguage.values.firstWhere((l) => l.code == savedCode, orElse: () => SupportedLanguage.zh);
        currentLanguage = saved;
        return;
      }
      currentLanguage = _getSystemLanguage();
    } catch (e, stackTrace) {
      logger.e('Failed to load saved language', error: e, stackTrace: stackTrace);
      currentLanguage = _getSystemLanguage();
    }
  }

  SupportedLanguage _getSystemLanguage() {
    final locale = WidgetsBinding.instance.platformDispatcher.locale;
    return switch (locale.languageCode) {
      'en' => SupportedLanguage.en,
      _ => SupportedLanguage.zh,
    };
  }

  List<SupportedLanguage> get supportedLanguages => SupportedLanguage.values;
  bool get isRTL => false;

  String t(String key, {String? defaultValue}) {
    if (!_isInitialized || _translations == null) {
      if (!_warnedKeys.contains(key)) {
        _warnedKeys.add(key);
        logger.d('I18nService not ready, using key as fallback: $key');
      }
      return defaultValue ?? key;
    }

    final keys = key.split('.');
    dynamic current = _translations;

    for (final k in keys) {
      if (current is YamlMap && current.containsKey(k)) {
        current = current[k];
      } else {
        return defaultValue ?? key;
      }
    }

    return current?.toString() ?? (defaultValue ?? key);
  }
}
