import 'dart:developer';

import 'package:get_time_ago/get_time_ago.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/ai_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/settings_service.dart';
import 'package:daily_satori/global.dart';

Future<void> initApp() async {
  initLogger();
  initGetTimeAgo();
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

void initGetTimeAgo() {
  GetTimeAgo.setDefaultLocale('zh');
}

Future<void> initServices() async {
  logger.i("开始初始化服务");
  await DBService.i.init();
  await SettingsService.i.init();
  await AiService.i.init();
  await ArticleService.i.init();
  await FileService.i.init();
  await HttpService.i.init();
  await ADBlockService.i.init();
  await BackupService.i.init();
}
