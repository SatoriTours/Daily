import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'translation_map.dart';

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
/// 负责管理应用的多语言支持，使用JSON配置文件存储翻译内容
/// 语言切换通过重启应用来实现，提高效率
class I18nService extends AppService {
  static const String _languageKey = 'app_language';

  /// 当前语言
  SupportedLanguage currentLanguage = SupportedLanguage.zh;

  /// 当前翻译映射
  late TranslationMap translations;

  /// 获取服务实例
  static I18nService get to => Get.find<I18nService>();

  @override
  String get serviceName => 'I18nService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  @override
  Future<void> init() async {
    await _loadSavedLanguage();
    await _loadTranslations();
    logger.i('I18nService initialized with language: ${currentLanguage.displayName}');
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
      final locale = WidgetsBinding.instance.platformDispatcher.locale;
      switch (locale.languageCode) {
        case 'zh':
          await changeLanguageAndRestart(SupportedLanguage.zh, Get.context!);
          break;
        case 'en':
          await changeLanguageAndRestart(SupportedLanguage.en, Get.context!);
          break;
        default:
          // 默认使用中文
          await changeLanguageAndRestart(SupportedLanguage.zh, Get.context!);
          break;
      }
    } catch (e, stackTrace) {
      logger.e('Failed to use system language', error: e, stackTrace: stackTrace);
      // 出错时使用中文作为默认
      await changeLanguageAndRestart(SupportedLanguage.zh, Get.context!);
    }
  }

  /// 加载翻译文件
  Future<void> _loadTranslations() async {
    try {
      final String jsonString = await rootBundle.loadString('assets/i18n/${currentLanguage.code}.json');
      final Map<String, dynamic> jsonMap = json.decode(jsonString);
      translations = TranslationMap.fromJson(jsonMap);
      logger.i('Loaded translations for language: ${currentLanguage.code}');
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
        final savedLanguage = SupportedLanguage.values
            .where((lang) => lang.code == savedLanguageCode)
            .firstOrNull;
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
    // 使用 Navigator 推送一个新路由并替换所有路由，模拟重启效果
    Navigator.of(context).pushNamedAndRemoveUntil(
      '/',
      (Route<dynamic> route) => false,
    );
  }

  /// 获取所有支持的语言列表
  List<SupportedLanguage> get supportedLanguages => SupportedLanguage.values;

  /// 检查是否为RTL语言
  bool get isRTL => false; // 目前支持的语言都是LTR，后续可扩展
}