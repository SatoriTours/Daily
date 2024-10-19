import 'package:drift/drift.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/global.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get i => _instance;

  Future<void> init() async {}

  AppDatabase get db => DBService.i.db;

  Future<void> saveArticle(ArticlesCompanion article) async {
    db.into(db.articles).insert(article);
    logger.i("文章已保存: ${firstLine(article.title.value ?? '')}");
  }

  Future<void> updateArticle(ArticlesCompanion article) async {
    var result = await (db.update(db.articles)
          ..where((row) => row.url.equals(article.url.value)))
        .replace(article);

    if (result) {
      logger.i("文章已更新: ${firstLine(article.title.value ?? '')}");
    } else {
      logger.i("未找到文章以更新: ${article.url}");
    }
  }

  Future<bool> isArticleExists(String url) async {
    final existingArticle =
        await (db.select(db.articles)..where((t) => t.url.equals(url))).get();
    return existingArticle.isNotEmpty;
  }

  Future<void> deleteArticle(int articleID) async {
    final result = await (db.delete(db.articles)
          ..where((row) => row.id.equals(articleID)))
        .go();

    if (result > 0) {
      logger.i("文章已删除: $articleID");
    } else {
      logger.i("未找到文章以删除: $articleID");
    }
  }

  Future<Article> getArticleById(int articleID) async {
    return await (db.select(db.articles)
          ..where((row) => row.id.equals(articleID)))
        .getSingle();
  }

  Future<bool> toggleFavorite(int articleID) async {
    var article = await getArticleById(articleID);
    final result = await (db.update(db.articles)
          ..where((row) => row.id.equals(article.id)))
        .write(ArticlesCompanion(
      isFavorite: Value(!article.isFavorite),
    ));

    if (result > 0) {
      logger
          .i(!article.isFavorite ? "文章已收藏: $articleID" : "文章已取消收藏: $articleID");
      return !article.isFavorite;
    } else {
      logger.i("未找到文章以更新收藏状态: $articleID");
    }
    return false;
  }

  Future<int> getMaxArticleID() async {
    return await (db.select(db.articles)..addColumns([db.articles.id.max()]))
        .get()
        .then((rows) {
      return rows.isNotEmpty ? rows.first.id : -1;
    });
  }

  Future<int> getMinArticleID() async {
    return await (db.select(db.articles)..addColumns([db.articles.id.min()]))
        .get()
        .then((rows) {
      return rows.isNotEmpty ? rows.first.id : -1;
    });
  }

  Future<List<Article>> getArticlesGreaterThanId(int articleID,
      {int limit = 20}) async {
    final articleDataList = await (db.select(db.articles)
          ..where((row) => row.id.isBiggerThanValue(articleID))
          ..orderBy([
            (row) => OrderingTerm(expression: row.id, mode: OrderingMode.desc)
          ])
          ..limit(limit))
        .get();

    return articleDataList;
  }

  Future<List<Article>> getArticlesLessThanId(int articleID,
      {int limit = 20}) async {
    final articleDataList = await (db.select(db.articles)
          ..where((row) => row.id.isSmallerThanValue(articleID))
          ..orderBy([
            (row) => OrderingTerm(expression: row.id, mode: OrderingMode.desc)
          ])
          ..limit(limit))
        .get();

    return articleDataList;
  }

  Future<List<Article>> getArticles({int limit = 20}) async {
    final articleDataList = await (db.select(db.articles)
          ..orderBy([
            (row) => OrderingTerm(expression: row.id, mode: OrderingMode.desc)
          ])
          ..limit(limit))
        .get();

    return articleDataList;
  }
}
