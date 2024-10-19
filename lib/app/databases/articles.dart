import 'package:drift/drift.dart';

class Articles extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text().nullable()();
  TextColumn get aiTitle => text().named('ai_title').nullable()();
  TextColumn get content => text().nullable()();
  TextColumn get aiContent => text().named('ai_content').nullable()();
  TextColumn get htmlContent => text().named('html_content').nullable()();
  TextColumn get url => text().withLength(min: 1, max: 255).unique()();
  TextColumn get imageUrl => text().named('image_url').nullable()();
  TextColumn get imagePath => text().named('image_path').nullable()();
  TextColumn get screenshotPath => text().named('screenshot_path').nullable()();
  BoolColumn get isFavorite => boolean().withDefault(const Constant(false))();
  DateTimeColumn get pubDate => dateTime().nullable()();
  TextColumn get comment => text().nullable()();

  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}
