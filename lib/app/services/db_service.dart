import 'dart:io';

import 'package:daily_satori/global.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:daily_satori/app/databases/database.dart';
import 'package:sentry_drift/sentry_drift.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

class DBService {
  DBService._privateConstructor();
  static final DBService _instance = DBService._privateConstructor();
  static DBService get i => _instance;

  static String get dbFileName => 'daily_satori.db.sqlite';

  Future<void> init() async {
    logger.i("[初始化服务] DBService");
    await _initDatabase();
  }

  Future<void> clear() async {
    logger.i("[清理化服务] DBService");
  }

  late AppDatabase _database;
  AppDatabase get db => _database;

  late String _dbPath;
  String get dbPath => _dbPath;

  Future<void> _initDatabase() async {
    final directory = await getApplicationDocumentsDirectory();
    _dbPath = path.join(directory.path, dbFileName);

    final SentryQueryExecutor executor = SentryQueryExecutor(
      () => NativeDatabase(File(_dbPath), logStatements: !isProduction),
      databaseName: dbFileName,
    );

    _database = AppDatabase(executor);
    await _database.customStatement('PRAGMA foreign_keys=ON'); // 等待数据库初始化完成
  }

  ISentrySpan startTransaction(String trName, String operation) {
    return Sentry.startTransaction("[DB]$trName", operation, bindToScope: true);
  }

  Future<void> stopTransaction(ISentrySpan tr) async {
    await tr.finish(status: const SpanStatus.ok());
  }
}
