import 'package:daily_satori/app/databases/article_screenshots.dart';
import 'package:daily_satori/app/databases/tags.dart';
import 'package:drift/drift.dart';

import 'package:daily_satori/app/databases/article_images.dart';
import 'package:daily_satori/app/databases/articles.dart';
import 'package:daily_satori/app/databases/database.steps.dart';
import 'package:daily_satori/app/databases/settings.dart';

part 'database.g.dart'; // 生成的代码文件

@DriftDatabase(tables: [Articles, Settings, ArticleImages, ArticleScreenshots, Tags, ArticleTags])
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.db);

  @override
  int get schemaVersion => 5;

  // static QueryExecutor _openConnection() {
  //   // driftDatabase from package:drift_flutter stores the database in
  //   // getApplicationDocumentsDirectory().
  //   return driftDatabase(name: dbFile);
  // }

  @override
  MigrationStrategy get migration {
    return MigrationStrategy(
      onUpgrade: stepByStep(from1To2: (m, schema) async {
        await m.createTable(schema.articleImages);
      }, from2To3: (Migrator m, Schema3 schema) async {
        await m.createTable(schema.articleScreenshoots);
      }, from3To4: (Migrator m, Schema4 schema) async {
        await m.renameTable(schema.articleScreenshots, 'article_screenshoots');
      }, from4To5: (Migrator m, Schema5 schema) async {
        await m.createTable(schema.tags);
        await m.createTable(schema.articleTags);
      }),
    );
  }
}
