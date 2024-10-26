import 'package:drift/drift.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/db_service.dart';
import 'package:daily_satori/global.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] ArticleService");
  }

  AppDatabase get db => DBService.i.db;

  Future<Article> saveArticle(ArticlesCompanion article) async {
    final newArticle = await db.into(db.articles).insertReturning(article);
    logger.i("文章已保存: ${firstLine(newArticle.title ?? '')}");

    return newArticle;
  }

  Future<Article?> updateArticle(ArticlesCompanion article) async {
    var result = await (db.update(db.articles)..where((row) => row.url.equals(article.url.value))).write(article);

    if (result >= 1) {
      logger.i("文章已更新: ${firstLine(article.title.value ?? '')}");
      // 返回更新后的 article 对象
      return await getFirstArticleByUrl(article.url.value);
    } else {
      logger.i("未找到文章以更新: ${article.url}");
      return null; // 未找到文章，返回 null
    }
  }

  Future<Article?> getFirstArticleByUrl(String url) async {
    final articles = await (db.select(db.articles)
          ..where((row) => row.url.equals(url))
          ..limit(1))
        .get();
    return articles.isNotEmpty ? articles.first : null;
  }

  Future<bool> isArticleExists(String url) async {
    final existingArticle = await (db.select(db.articles)..where((t) => t.url.equals(url))).get();
    return existingArticle.isNotEmpty;
  }

  Future<void> deleteArticle(int articleID) async {
    // 删除的时候,要把相关的图片文件都删除
    final result = await (db.delete(db.articles)..where((row) => row.id.equals(articleID))).go();

    if (result > 0) {
      logger.i("文章已删除: $articleID");
    } else {
      logger.i("未找到文章以删除: $articleID");
    }
  }

  Future<Article> getArticleById(int articleID) async {
    return await (db.select(db.articles)..where((row) => row.id.equals(articleID))).getSingle();
  }

  Future<bool> toggleFavorite(int articleID) async {
    var article = await getArticleById(articleID);
    final result = await (db.update(db.articles)..where((row) => row.id.equals(article.id))).write(ArticlesCompanion(
      isFavorite: Value(!article.isFavorite),
    ));

    if (result > 0) {
      logger.i(!article.isFavorite ? "文章已收藏: $articleID" : "文章已取消收藏: $articleID");
      return !article.isFavorite;
    } else {
      logger.i("未找到文章以更新收藏状态: $articleID");
    }
    return false;
  }

  // Future<int> getMaxArticleID() async {
  //   return await (db.select(db.articles)..addColumns([db.articles.id.max()])).get().then((rows) {
  //     return rows.isNotEmpty ? rows.first.id : -1;
  //   });
  // }

  // Future<int> getMinArticleID() async {
  //   return await (db.select(db.articles)..addColumns([db.articles.id.min()])).get().then((rows) {
  //     return rows.isNotEmpty ? rows.first.id : -1;
  //   });
  // }

  SimpleSelectStatement<$ArticlesTable, Article> getArticlesGreaterThanId(int articleID, {int limit = 20}) {
    final articleDataList = db.select(db.articles);

    articleDataList
      ..where((t) => t.id.isBiggerThanValue(articleID))
      ..orderBy([(t) => OrderingTerm(expression: t.id, mode: OrderingMode.desc)])
      ..limit(limit);

    return articleDataList;
  }

  SimpleSelectStatement<$ArticlesTable, Article> getArticlesLessThanId(int articleID, {int limit = 20}) {
    final articleDataList = db.select(db.articles);

    articleDataList
      ..where((t) => t.id.isSmallerThanValue(articleID))
      ..orderBy([(t) => OrderingTerm(expression: t.id, mode: OrderingMode.desc)])
      ..limit(limit);

    return articleDataList;
  }

  SimpleSelectStatement<$ArticlesTable, Article> getArticles({int limit = 20}) {
    final articleDataList = db.select(db.articles);

    articleDataList
      ..orderBy([(t) => OrderingTerm(expression: t.id, mode: OrderingMode.desc)])
      ..limit(limit);

    return articleDataList;
  }
}
