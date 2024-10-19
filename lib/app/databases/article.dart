import 'package:drift/drift.dart';

class Article extends Table {
  @override
  String get tableName => 'articles';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text()();
  TextColumn get aiTitle => text().named('ai_title')();
  TextColumn get content => text()();
  TextColumn get aiContent => text().named('ai_content')();
  TextColumn get htmlContent => text().named('html_content')();
  TextColumn get url => text().nullable()();
  TextColumn get imageUrl => text().named('image_url')();
  TextColumn get imagePath => text().named('image_path')();
  TextColumn get screenshotPath => text().named('screenshot_path')();
  IntColumn get isFavorite => integer().withDefault(const Constant(0))();
  DateTimeColumn get pubDate => dateTime().nullable()();
  TextColumn get comment => text()();

  DateTimeColumn get updatedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().nullable()();
}
