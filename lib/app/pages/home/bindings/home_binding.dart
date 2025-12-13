import 'package:daily_satori/app/pages/books/bindings/books_binding.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:get/get.dart';
import 'package:flutter/foundation.dart';
import '../controllers/home_controller.dart';
import 'package:daily_satori/app/pages/articles/bindings/articles_binding.dart';
import 'package:daily_satori/app/pages/diary/bindings/diary_binding.dart';
import 'package:daily_satori/app/pages/settings/bindings/settings_binding.dart';
import 'package:daily_satori/app/pages/ai_chat/bindings/ai_chat_binding.dart';
import 'package:daily_satori/app/pages/weekly_summary/bindings/weekly_summary_binding.dart';

/// HomeBinding: 主页依赖注入
/// 负责:
/// 1. 注入主页控制器
/// 2. 初始化子页面依赖
class HomeBinding extends Binding {
  static const String _tag = 'HomeBinding';

  @override
  List<Bind> dependencies() {
    _initializeSubModules();
    _logBinding();

    return [Bind.lazyPut<HomeController>(() => HomeController())];
  }

  /// 初始化子模块依赖
  void _initializeSubModules() {
    ArticlesBinding().dependencies();
    DiaryBinding().dependencies();
    SettingsBinding().dependencies();
    BooksBinding().dependencies();
    AIChatBinding().dependencies();
    WeeklySummaryBinding().dependencies();

    if (kDebugMode) {
      logger.i('子模块依赖初始化完成 [$_tag]');
    }
  }

  /// 记录绑定日志
  void _logBinding() {
    if (kDebugMode) {
      logger.i('主页模块依赖注入完成 [$_tag]');
    }
  }
}
