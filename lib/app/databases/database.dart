import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

import 'package:daily_satori/app/databases/article_images.dart';
import 'package:daily_satori/app/databases/articles.dart';
import 'package:daily_satori/app/databases/database.steps.dart';
import 'package:daily_satori/app/databases/settings.dart';

part 'database.g.dart'; // 生成的代码文件

@DriftDatabase(tables: [Articles, Settings, ArticleImages])
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  static String dbFile = 'daily_satori.db';

  @override
  int get schemaVersion => 2;

  static QueryExecutor _openConnection() {
    // driftDatabase from package:drift_flutter stores the database in
    // getApplicationDocumentsDirectory().
    return driftDatabase(name: dbFile);
  }

  @override
  MigrationStrategy get migration {
    return MigrationStrategy(
      onUpgrade: stepByStep(from1To2: (m, schema) async {
        await m.createTable(schema.articleImages);
      }),
    );
  }
}
