import 'package:get/get.dart';
import '../controllers/ai_chat_controller.dart';

/// AI聊天助手模块绑定
class AIChatBinding extends Binding {
  @override
  List<Bind> dependencies() {
    return [Bind.lazyPut<AIChatController>(() => AIChatController())];
  }
}
