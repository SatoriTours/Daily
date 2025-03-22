import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/flutter_service.dart';
import 'package:daily_satori/app/services/font_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/share_receive_service.dart';
import 'package:daily_satori/app/services/time_service.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';

// 应用加载好前执行
Future<void> initApp() async {
  await _initBasicServices();
  await _initParallelServices();
  await _initNonBlockingServices();

  // 初始化网页解析服务 (在基础服务和并行服务之后)
  await WebpageParserService.i.init();

  logger.i('所有服务初始化完成');
}

// 应用准备好之后执行(主要是UI准备好)
void onAppReady() {
  ShareReceiveService.i.init();
}

// 应用退出时执行(目前没使用)
Future<void> clearApp() async {
  ObjectboxService.i.dispose();

  // 关闭网页解析服务
  WebpageParserService.i.dispose();
}

// 初始化基础的服务，所有服务都需要依赖这些基础服务
Future<void> _initBasicServices() async {
  await LoggerService.i.init(); // 初始化日志
  await FlutterService.i.init(); // 初始化Flutter
  await TimeService.i.init(); // 初始化时间
  await ObjectboxService.i.init(); // 初始化数据库
  await SettingService.i.init(); // 从数据库里面加载配置
  await FileService.i.init(); // 初始化文件目录服务
  await HttpService.i.init(); // 初始化HTTP服务
}

Future<void> _initParallelServices() async {
  // 可以并行执行的初始化任务
  await Future.wait([
    FontService.i.init(), // 初始化字体
    AiService.i.init(), // 初始化AI服务
    ADBlockService.i.init(), // 初始化广告拦截服务
    FreeDiskService.i.init(), // 初始化磁盘服务
    AppUpgradeService.i.init(), // 检查是否有新版本可以安装
    WebpageParserService.i.init(), // 初始化网页解析服务
  ]);
}

// 初始化那些不需要等待的服务
Future<void> _initNonBlockingServices() async {
  BackupService.i.init(); // 备份服务
  PluginService.i.init(); // 初始化插件服务, 用来更新插件
  WebService.i.init(); // 初始化Web服务
}
