import 'dart:developer';

import 'package:daily_satori/app/helpers/sqlite_settings_provider.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_settings_screens/flutter_settings_screens.dart';

import 'package:get_time_ago/get_time_ago.dart';
import 'package:google_fonts/google_fonts.dart';
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
  await initFonts();
  initLogger();
  initGetTimeAgo();
  await initServices();
}

Future<void> initFonts() async {
  if (isProduction) {
    GoogleFonts.config.allowRuntimeFetching = false;
    LicenseRegistry.addLicense(() async* {
      final license = await rootBundle.loadString('assets/fonts/google/OFL.txt');
      yield LicenseEntryWithLineBreaks(['assets/fonts/google'], license);
    });
  }
}

class MyConsoleOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    log(event.lines.join("\n"), name: "Satori");
  }
}

void initLogger() {
  if (isProduction) {
    Logger.level = Level.info;
  }

  logger = Logger(
    printer: SimplePrinter(colors: false),
    output: MyConsoleOutput(),
  );
}

void initGetTimeAgo() {
  GetTimeAgo.setDefaultLocale('zh');
}

Future<void> initSettings() async {
  logger.i('[初始化] Settings SqliteProvider');
  await Settings.init(cacheProvider: SqliteProvider());
}

Future<void> initServices() async {
  logger.i("开始初始化服务");

  // 基础的需要先初始化的任务
  await DBService.i.init(); // 初始化数据库
  await SettingsService.i.init(); // 从数据库里面加载配置
  await initSettings();
  await FileService.i.init(); // 初始化文件目录服务

  // 可以并行执行的初始化任务
  await Future.wait([
    AiService.i.init(),
    ArticleService.i.init(),
    HttpService.i.init(),
    ADBlockService.i.init(),
    FreeDiskService.i.init(),
  ]);

  // 不用等待处理完成的服务, 可以在后台执行
  BackupService.i.init();
}
