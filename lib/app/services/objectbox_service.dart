import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/objectbox.g.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// 对象存储服务类
///
/// 负责管理ObjectBox数据库的初始化、访问和操作
class ObjectboxService implements AppService {
  ObjectboxService._();
  static final ObjectboxService _instance = ObjectboxService._();
  static ObjectboxService get i => _instance;

  @override
  String get serviceName => 'ObjectboxService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  /// 数据库目录名（已迁移至 DatabaseConfig）
  static String get dbDir => DatabaseConfig.objectBoxDir;

  /// ObjectBox 存储实例
  late final Store _store;

  /// 获取存储实例
  Store get store => _store;

  /// ObjectBox Admin 实例
  late Admin _admin;

  /// 初始化 ObjectBox 服务
  @override
  Future<void> init() async {
    // 创建数据库目录并打开存储
    final docsDir = await getApplicationDocumentsDirectory();
    final dbPath = path.join(docsDir.path, dbDir);
    _store = await openStore(directory: dbPath);

    // 开发环境启用 Admin
    _initAdminIfNeeded();
  }

  /// 在开发环境中初始化管理界面
  void _initAdminIfNeeded() {
    if (!AppInfoUtils.isProduction && Admin.isAvailable()) {
      // logger.i("[存储服务] 启用管理界面: http://0.0.0.0:9000");
      _admin = Admin(store, bindUri: 'http://0.0.0.0:9000');
    }
  }

  /// 释放资源
  @override
  void dispose() {
    _store.close();
    if (!AppInfoUtils.isProduction && Admin.isAvailable()) {
      _admin.close();
    }
  }

  /// 获取指定类型的 Box
  Box<T> box<T>() => _store.box<T>();

  /// 清空所有数据
  void clearAll() {
    _store.box<Article>().removeAll();
    _store.box<Tag>().removeAll();
    _store.box<Image>().removeAll();
    _store.box<Screenshot>().removeAll();
    logger.i("[存储服务] 已清空所有数据");
  }
}
