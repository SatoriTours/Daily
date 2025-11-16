import 'package:get/get.dart';

/// 文章日历控制器
/// 管理日历对话框的状态
class ArticleCalendarController extends GetxController {
  /// 当前显示的月份
  final displayedMonth = DateTime.now().obs;

  /// 选中的日期
  final selectedDate = DateTime.now().obs;

  @override
  void onInit() {
    super.onInit();
    // 初始化时设置为当月第一天
    final now = DateTime.now();
    displayedMonth.value = DateTime(now.year, now.month, 1);
    selectedDate.value = now;
  }

  /// 上一月
  void previousMonth() {
    displayedMonth.value = DateTime(
      displayedMonth.value.year,
      displayedMonth.value.month - 1,
      1,
    );
  }

  /// 下一月
  void nextMonth() {
    displayedMonth.value = DateTime(
      displayedMonth.value.year,
      displayedMonth.value.month + 1,
      1,
    );
  }

  /// 选择日期
  void selectDate(DateTime date) {
    selectedDate.value = date;
  }

  /// 判断是否是今天
  bool isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year && date.month == now.month && date.day == now.day;
  }

  /// 判断两个日期是否是同一天
  bool isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }
}
