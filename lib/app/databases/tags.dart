import 'package:daily_satori/app/databases/articles.dart';
import 'package:drift/drift.dart';

class Tags extends Table {
  IntColumn get id => integer().autoIncrement()();

  TextColumn get title => text().nullable()();
  TextColumn get icon => text().nullable()();

  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  List<Set<Column>> get uniqueKeys => [
        {title}
      ];
}

class ArticleTags extends Table {
  IntColumn get articleId => integer().references(Articles, #id)();
  IntColumn get tagId => integer().references(Tags, #id)();

  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  List<Set<Column>> get uniqueKeys => [
        {articleId, tagId}
      ];
}
