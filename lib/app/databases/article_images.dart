import 'package:drift/drift.dart';

import 'package:daily_satori/app/databases/articles.dart';

class ArticleImages extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get article => integer().nullable().references(Articles, #id)();

  TextColumn get imageUrl => text().named('image_url').nullable()();
  TextColumn get imagePath => text().named('image_path').nullable()();

  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}
