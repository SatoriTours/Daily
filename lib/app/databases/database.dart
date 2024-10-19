import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

import 'package:daily_satori/app/databases/articles.dart';
import 'package:daily_satori/app/databases/settings.dart';

part 'database.g.dart'; // 生成的代码文件

@DriftDatabase(tables: [Articles, Settings])
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  static String dbFile = 'daily_satori.db';

  @override
  int get schemaVersion => 1;

  static QueryExecutor _openConnection() {
    // driftDatabase from package:drift_flutter stores the database in
    // getApplicationDocumentsDirectory().
    return driftDatabase(name: dbFile);
  }
}
