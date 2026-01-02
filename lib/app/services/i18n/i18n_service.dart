import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:yaml/yaml.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/data/setting/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';

/// 支持的语言枚举
enum SupportedLanguage {
  zh('zh', '中文'),
  en('en', 'English');

  const SupportedLanguage(this.code, this.displayName);

  final String code;
  final String displayName;
}

/// 国际化服务
///
/// 负责管理应用的多语言支持，使用YAML配置文件存储翻译内容
/// 语言切换通过重启应用来实现，提高效率
class I18nService implements AppService {
  static const String _languageKey = 'app_language';

  // MARK: - 单例实现
  I18nService._privateConstructor();
  static final I18nService _instance = I18nService._privateConstructor();
  static I18nService get i => _instance;

  /// 当前语言
  SupportedLanguage currentLanguage = SupportedLanguage.zh;

  /// 当前翻译映射
  YamlMap? _translations;

  /// 服务是否已初始化
  bool _isInitialized = false;

  /// 已警告的键集合（避免重复警告）
  final Set<String> _warnedKeys = {};

  @override
  String get serviceName => 'I18nService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  @override
  Future<void> init() async {
    await _loadSavedLanguage();
    await _loadTranslations();
    _isInitialized = true;
    logger.i('I18nService initialized with language: ${currentLanguage.displayName}');
  }

  @override
  Future<void> dispose() async {
    // I18nService 不需要特殊的清理逻辑
    logger.i('I18nService disposed');
  }

  /// 切换语言并重启应用
  Future<void> changeLanguageAndRestart(SupportedLanguage language, BuildContext context) async {
    if (currentLanguage == language) return;

    try {
      // 保存语言设置
      await _saveLanguage(language);

      logger.i('Language changed to: ${language.displayName}, restarting app...');

      // 确保context依然有效
      if (!context.mounted) return;

      // 重启应用
      _restartApp(context);
    } catch (e, stackTrace) {
      logger.e('Failed to change language', error: e, stackTrace: stackTrace);
    }
  }

  /// 根据系统语言自动选择
  Future<void> useSystemLanguage() async {
    try {
      final context = AppNavigation.navigatorKey.currentContext;
      if (context == null) {
        logger.w('useSystemLanguage: No valid context available');
        return;
      }

      final locale = WidgetsBinding.instance.platformDispatcher.locale;
      final targetLanguage = switch (locale.languageCode) {
        'zh' => SupportedLanguage.zh,
        'en' => SupportedLanguage.en,
        _ => SupportedLanguage.zh, // 默认使用中文
      };

      await changeLanguageAndRestart(targetLanguage, context);
    } catch (e, stackTrace) {
      logger.e('Failed to use system language', error: e, stackTrace: stackTrace);
      // 出错时尝试使用中文作为默认，获取新的 context 以避免跨 async gap 问题
      final newContext = AppNavigation.navigatorKey.currentContext;
      if (newContext != null && newContext.mounted) {
        await changeLanguageAndRestart(SupportedLanguage.zh, newContext);
      }
    }
  }

  /// 加载翻译文件
  Future<void> _loadTranslations() async {
    try {
      final String yamlString = await rootBundle.loadString('assets/i18n/${currentLanguage.code}.yaml');
      _translations = loadYaml(yamlString);
      logger.i('Loaded YAML translations for language: ${currentLanguage.code}');
    } catch (e, stackTrace) {
      logger.e('Failed to load translations for ${currentLanguage.code}', error: e, stackTrace: stackTrace);

      // 加载失败时使用中文作为备用
      if (currentLanguage != SupportedLanguage.zh) {
        logger.i('Falling back to Chinese translations');
        currentLanguage = SupportedLanguage.zh;
        await _loadTranslations();
      }
    }
  }

  /// 加载保存的语言设置
  Future<void> _loadSavedLanguage() async {
    try {
      final savedLanguageCode = SettingRepository.i.getSetting(_languageKey);
      if (savedLanguageCode.isNotEmpty) {
        final savedLanguage = SupportedLanguage.values.where((lang) => lang.code == savedLanguageCode).firstOrNull;
        if (savedLanguage != null) {
          currentLanguage = savedLanguage;
          return;
        }
      }

      // 没有保存的语言设置，使用系统语言
      await _useSystemLanguageWithoutRestart();
    } catch (e, stackTrace) {
      logger.e('Failed to load saved language', error: e, stackTrace: stackTrace);
      // 出错时使用系统语言
      await _useSystemLanguageWithoutRestart();
    }
  }

  /// 使用系统语言（不重启）
  Future<void> _useSystemLanguageWithoutRestart() async {
    try {
      final locale = WidgetsBinding.instance.platformDispatcher.locale;
      switch (locale.languageCode) {
        case 'zh':
          currentLanguage = SupportedLanguage.zh;
          break;
        case 'en':
          currentLanguage = SupportedLanguage.en;
          break;
        default:
          currentLanguage = SupportedLanguage.zh;
          break;
      }
    } catch (e, stackTrace) {
      logger.e('Failed to get system language', error: e, stackTrace: stackTrace);
      currentLanguage = SupportedLanguage.zh;
    }
  }

  /// 保存语言设置
  Future<void> _saveLanguage(SupportedLanguage language) async {
    try {
      SettingRepository.i.saveSetting(_languageKey, language.code);
    } catch (e, stackTrace) {
      logger.e('Failed to save language', error: e, stackTrace: stackTrace);
    }
  }

  /// 重启应用
  void _restartApp(BuildContext context) {
    // 使用 go_router 导航到首页，模拟重启效果
    AppNavigation.offAllNamed(Routes.home);
  }

  /// 获取所有支持的语言列表
  List<SupportedLanguage> get supportedLanguages => SupportedLanguage.values;

  /// 检查是否为RTL语言
  bool get isRTL => false; // 目前支持的语言都是LTR，后续可扩展

  /// 翻译文本
  ///
  /// 支持点分隔符的嵌套键访问，如 "error.network"
  String t(String key, {String? defaultValue}) {
    // 如果服务未初始化或翻译映射为空，返回 key 或默认值
    if (!_isInitialized || _translations == null) {
      // 只在第一次遇到某个键时警告，避免日志泛滥
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
