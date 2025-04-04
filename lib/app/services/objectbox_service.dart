import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:objectbox/objectbox.dart';

import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';

/// 对象存储服务类
///
/// 负责管理ObjectBox数据库的初始化、访问和操作
class ObjectboxService {
  // MARK: - 单例实现
  ObjectboxService._();
  static final ObjectboxService _instance = ObjectboxService._();
  static ObjectboxService get i => _instance;

  // MARK: - 常量

  /// 数据库目录名
  static const String dbDir = 'obx-daily';

  // MARK: - 属性

  /// ObjectBox 存储实例
  late final Store _store;

  /// 获取存储实例
  Store get store => _store;

  /// ObjectBox Admin 实例
  late Admin _admin;

  // MARK: - 初始化与释放

  /// 初始化 ObjectBox 服务
  Future<void> init() async {
    logger.i("[存储服务] 初始化");

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
      logger.i("[存储服务] 启用管理界面: http://0.0.0.0:9000");
      _admin = Admin(store, bindUri: 'http://0.0.0.0:9000');
    }
  }

  /// 释放资源
  void dispose() {
    _store.close();
    if (!AppInfoUtils.isProduction && Admin.isAvailable()) {
      _admin.close();
    }
  }

  // MARK: - 数据访问

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
