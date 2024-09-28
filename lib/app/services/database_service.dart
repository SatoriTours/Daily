import 'dart:collection';

import 'package:daily_satori/global.dart';
import 'package:get/get.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

class DatabaseService extends GetxService {
  static const String _database = 'daily_satori.db';

  Future<DatabaseService> init() async {
    await initDatabase();
    return this;
  }

  static DatabaseService get to => Get.find<DatabaseService>();

  late Database db;

  Future<String> dbFilePath() async {
    return path.join(await getDatabasesPath(), _database);
  }

  Future<DatabaseService> initDatabase() async {
    logger.i("开始启动数据库服务");
    var dbPath = await dbFilePath();
    logger.d("打开数据库: $dbPath");
    db = await openDatabase(
      dbPath,
      version: _migrations.length,
      onCreate: (db, version) async {
        logger.d("创建数据库 version: $version");
        var batch = db.batch();
        _migrations.forEach((name, sqls) {
          logger.d("执行数据库迁移脚本: $name");
          for (var sql in sqls) {
            batch.execute(sql);
          }
        });
        await batch.commit();
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        logger.d("更新数据库 version: $oldVersion => $newVersion");
        var batch = db.batch();
        var index = 1;
        _migrations.forEach((name, sqls) {
          if (index > oldVersion && index <= newVersion) {
            logger.d("执行数据库迁移脚本: $name");
            for (var sql in sqls) {
              batch.execute(sql);
            }
          }
          index++;
        });
        await batch.commit();
      },
      onDowngrade: onDatabaseDowngradeDelete,
    );
    logger.i("数据库当前版本: ${await db.getVersion()}");
    return this;
  }

  final LinkedHashMap<String, List<String>> _migrations =
      LinkedHashMap<String, List<String>>.from({
    "01_初始化数据库": [
      '''CREATE TABLE IF NOT EXISTS articles (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          title TEXT,
          ai_title TEXT,
          content TEXT,
          ai_content TEXT,
          url TEXT,
          image_url TEXT,  -- 文章banner图片URL
          image_path TEXT, -- 图片本地保存路径
          is_read INTEGER DEFAULT 0,
          is_favorite INTEGER DEFAULT 0,
          pub_date DATETIME, -- 文章的发布日期

          tag_id INTEGER,

          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (url) REFERENCES article_urls(url)
      )''',
      '''CREATE TABLE IF NOT EXISTS tags (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )'''
    ],
    "02_创建配置表": [
      '''CREATE TABLE IF NOT EXISTS settings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT UNIQUE,
        value TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )'''
    ],
  });
}
