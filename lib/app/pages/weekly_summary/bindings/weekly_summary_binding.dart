import 'package:get/get.dart';
import '../controllers/weekly_summary_controller.dart';

/// 周报页面绑定
class WeeklySummaryBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut(() => WeeklySummaryController())];
  }
}
