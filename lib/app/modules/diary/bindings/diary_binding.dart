import 'package:get/get.dart';

import '../controllers/diary_controller.dart';

class DiaryBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<DiaryController>(() => DiaryController())];
  }
}
