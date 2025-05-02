import 'package:daily_satori/app_exports.dart';
import 'package:flutter/foundation.dart';

/// HomeController: 主页控制器
/// 职责：
/// 1. 管理底部导航栏状态
/// 2. 处理页面切换逻辑
/// 3. 维护页面生命周期
class HomeController extends BaseController {
  static const String _tag = 'HomeController';

  /// 当前选中的页面索引
  /// 0: 文章页面
  /// 1: 日记页面
  /// 2: 读书页面
  /// 3: 设置页面
  final currentIndex = 0.obs;

  @override
  void onInit() {
    super.onInit();
    _logPageInit();
  }

  /// 切换页面
  /// [index] 目标页面索引
  void changePage(int index) {
    if (index == currentIndex.value) return;

    final oldIndex = currentIndex.value;
    currentIndex.value = index;

    if (kDebugMode) {
      logger.i('页面切换: $oldIndex -> $index [$_tag:${DateTime.now()}]');
    }
  }

  /// 记录页面初始化日志
  void _logPageInit() {
    if (kDebugMode) {
      logger.i('主页初始化完成 [$_tag:${DateTime.now()}]');
    }
  }
}
