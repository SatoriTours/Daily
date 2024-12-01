import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/font_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/sentry_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/share_receive_service.dart';
import 'package:daily_satori/app/services/tags_service.dart';
import 'package:daily_satori/app/services/time_service.dart';

// 应用加载好前执行
Future<void> initApp() async {
  await _initBasicServices();
  await _initParallelServices();
  _initNonBlockingServices();
}

// 应用准备好之后执行(主要是UI准备好)
void onAppReady() {
  ShareReceiveService.i.init();
  ObjectboxService.i.checkAndMigrateFromSQLite();
}

// 应用退出时执行(目前没使用)
Future<void> clearApp() async {
  ObjectboxService.i.dispose();
}

// 初始化基础的服务，所有服务都需要依赖这些基础服务
Future<void> _initBasicServices() async {
  await LoggerService.i.init(); // 初始化日志
  await TimeService.i.init(); // 初始化时间
  await ObjectboxService.i.init(); // 初始化数据库
  await SettingService.i.init(); // 从数据库里面加载配置
  await FileService.i.init(); // 初始化文件目录服务
}

Future<void> _initParallelServices() async {
  // 可以并行执行的初始化任务
  await Future.wait([
    SentryService.i.init(), // 初始化Sentry服务
    TagsService.i.init(), // 初始化标签服务
    FontService.i.init(), // 初始化字体
    AiService.i.init(), // 初始化AI服务
    ArticleService.i.init(), // 初始化文章服务
    HttpService.i.init(), // 初始化HTTP服务
    ADBlockService.i.init(), // 初始化广告拦截服务
    FreeDiskService.i.init(), // 初始化磁盘服务
  ]);
}

// 初始化不需要等待的服务
void _initNonBlockingServices() {
  AppUpgradeService.i.init(); // 检查是否有新版本可以安装
  BackupService.i.init(); // 备份服务
}
