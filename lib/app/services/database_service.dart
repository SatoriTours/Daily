import 'dart:collection';

import 'package:daily_satori/global.dart';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

class DatabaseService {
  DatabaseService._privateConstructor();
  static final DatabaseService _instance = DatabaseService._privateConstructor();
  static DatabaseService get instance => _instance;

  Future<void> init() async {
    await initDatabase();
  }

  static const String _databaseFile = 'daily_satori.db';

  late Database _database;
  Database get database => _database;

  Future<String> dbFilePath() async {
    return path.join(await getDatabasesPath(), _databaseFile);
  }

  Future<DatabaseService> initDatabase() async {
    logger.i("开始启动数据库服务");
    var dbPath = await dbFilePath();
    logger.d("打开数据库: $dbPath");
    _database = await openDatabase(
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
    logger.i("数据库当前版本: ${await database.getVersion()}");
    return this;
  }

  final LinkedHashMap<String, List<String>> _migrations = LinkedHashMap<String, List<String>>.from({
    "01_初始化数据库": [
      '''CREATE TABLE IF NOT EXISTS articles (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          title TEXT,
          ai_title TEXT,
          content TEXT,
          ai_content TEXT,
          html_content TEXT,
          url TEXT UNIQUE, -- 根据url来判断唯一使得同一篇文章不会被保存多次
          image_url TEXT,  -- 文章banner图片URL
          image_path TEXT, -- 图片本地保存路径
          is_read INTEGER DEFAULT 0,
          is_favorite INTEGER DEFAULT 0,
          pub_date DATETIME, -- 文章的发布日期
          comment TEXT, -- 文章备注

          tag_id INTEGER,

          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )''',
      '''CREATE TABLE IF NOT EXISTS tags (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )''',
      '''CREATE TABLE IF NOT EXISTS articles_tags (
          article_id INTEGER,
          tag_id INTEGER,
          PRIMARY KEY (article_id, tag_id),
          FOREIGN KEY (article_id) REFERENCES articles(id),
          FOREIGN KEY (tag_id) REFERENCES tags(id)
      )''',
    ],
    "02_创建配置表": [
      '''CREATE TABLE IF NOT EXISTS settings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT UNIQUE,
        value TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )''',
    ],
    "03_增加文章截图字段": [
      '''ALTER TABLE articles ADD COLUMN screenshot_path TEXT;''',
    ],
  });
}
