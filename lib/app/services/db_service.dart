import 'dart:io';

import 'package:daily_satori/global.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:daily_satori/app/databases/database.dart';

class DBService {
  DBService._privateConstructor();
  static final DBService _instance = DBService._privateConstructor();
  static DBService get i => _instance;

  static String get dbFileName => 'daily_satori.db.sqlite';

  Future<void> init() async {
    logger.i("[初始化服务] DBService");
    await _initDatabase();
  }

  late AppDatabase _database;
  AppDatabase get db => _database;

  late String _dbPath;
  String get dbPath => _dbPath;

  Future<void> _initDatabase() async {
    final directory = await getApplicationDocumentsDirectory();
    _dbPath = path.join(directory.path, dbFileName);

    _database = AppDatabase(NativeDatabase(File(_dbPath), logStatements: !isProduction));
    await _database.customStatement('PRAGMA foreign_keys=ON'); // 等待数据库初始化完成
  }
}
