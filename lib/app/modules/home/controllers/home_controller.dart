import 'package:get/get.dart';
import 'package:daily_satori/app_exports.dart';

/// 主页控制器，负责管理底部导航栏
class HomeController extends BaseController {
  /// 当前选中的页面索引
  final currentIndex = 0.obs;

  /// 切换页面
  void changePage(int index) {
    currentIndex.value = index;
  }
}
