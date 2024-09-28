import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/global.dart';
import 'package:get/get.dart';
import 'package:logger/logger.dart';

Future<void> initApp() async {
  initLogger();
  await initServices();
  // initShareReceive();
}

void initLogger() {
  if (isProduction) {
    Logger.level = Level.error;
  }
  logger = Logger(
    printer: PrettyPrinter(),
    output: null,
  );
}

Future<void> initServices() async {
  logger.i("开始初始化服务");
  await Get.put(DatabaseService()).init();
}

// const platform = MethodChannel('tours.sator.daily/share');
// void initShareReceive() {
//   platform.setMethodCallHandler((call) async {
//     logger.i("接收到原生 android 的消息: ${call.method}");
//     if (call.method == 'receiveSharedText') {
//       shareText = call.arguments;
//       Get.toNamed(Routes.SHARE_DIALOG);
//     }
//   });
// }
