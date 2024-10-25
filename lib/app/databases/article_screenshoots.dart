import 'package:drift/drift.dart';

import 'package:daily_satori/app/databases/articles.dart';

class ArticleScreenshoots extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get article => integer().nullable().references(Articles, #id)();

  TextColumn get imagePath => text().named('image_path').nullable()();

  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}
