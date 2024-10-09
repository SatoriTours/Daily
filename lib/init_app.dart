import 'dart:developer';

import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/app/services/settings_service.dart';
import 'package:daily_satori/global.dart';
import 'package:logger/logger.dart';

Future<void> initApp() async {
  initLogger();
  await initServices();
}

class MyConsoleOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    log(event.lines.join("\n"), name: "Satori");
  }
}

void initLogger() {
  if (isProduction) {
    Logger.level = Level.error;
  }

  logger = Logger(
    printer: SimplePrinter(colors: false),
    output: MyConsoleOutput(),
  );
}

Future<void> initServices() async {
  logger.i("开始初始化服务");
  await DatabaseService.instance.init();
  await SettingsService.instance.init();
}
