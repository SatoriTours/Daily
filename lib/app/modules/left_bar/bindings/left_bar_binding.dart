import 'package:get/get.dart';

import '../controllers/left_bar_controller.dart';

class LeftBarBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<LeftBarController>(() => LeftBarController())];
  }
}
