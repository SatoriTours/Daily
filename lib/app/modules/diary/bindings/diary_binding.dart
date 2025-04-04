import 'package:get/get.dart';

import '../controllers/diary_controller.dart';

/// 日记绑定
class DiaryBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<DiaryController>(() => DiaryController())];
  }
}
