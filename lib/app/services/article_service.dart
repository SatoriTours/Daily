import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] ArticleService");
  }

  final articleBox = ObjectboxService.i.box<Article>();

  Future<Article?> saveArticle(Article article) async {
    try {
      article.id = articleBox.put(article);
      logger.i("文章已保存: ${firstLine(article.title ?? '')}");
      return article;
    } catch (e) {
      logger.e("[保存文章失败] $e");
      return null;
    }
  }

  Future<Article?> updateArticle(int articleID, Article article) async {
    final existing = articleBox.get(articleID);
    if (existing == null) {
      logger.i("未找到文章以更新: ${article.url}");
      return null;
    }

    article.id = articleID;
    article.id = articleBox.put(article);
    logger.i("文章已更新: ${firstLine(article.title ?? '')}");
    return article;
  }

  Future<Article?> getFirstArticleByUrl(String url) async {
    final query = articleBox.query(Article_.url.equals(url));
    return query.build().findFirst();
  }

  Future<bool> isArticleExists(String url) async {
    final query = articleBox.query(Article_.url.equals(url)).build();
    final existingArticle = query.findFirst();
    return existingArticle != null;
  }

  Future<void> deleteArticle(int articleID) async {
    final article = articleBox.get(articleID);
    if (article == null) {
      logger.i("未找到文章以删除: $articleID");
      return;
    }

    // 删除文章和关联的标签
    article.tags.clear();
    article.images.clear();
    article.screenshots.clear();
    articleBox.remove(articleID);
    logger.i("文章已删除: $articleID");
  }

  Future<Article?> getArticleById(int articleID) async {
    return articleBox.get(articleID);
  }

  Future<bool> toggleFavorite(int articleID) async {
    final article = await getArticleById(articleID);
    if (article == null) {
      logger.i("未找到文章以更新收藏状态: $articleID");
      return false;
    }

    article.isFavorite = !article.isFavorite;
    articleBox.put(article);

    logger.i(article.isFavorite ? "文章已收藏: $articleID" : "文章已取消收藏: $articleID");
    return article.isFavorite;
  }

  List<Article> getArticlesGreaterThanId(int articleID, {int limit = 20}) {
    final query = articleBox
        .query(Article_.id.greaterThan(articleID))
        .order(Article_.id, flags: Order.descending)
        .build();

    query.limit = limit;

    final articles = query.find();
    return articles;
  }

  List<Article> getArticlesLessThanId(int articleID, {int limit = 20}) {
    final query = articleBox
        .query(Article_.id.lessThan(articleID))
        .order(Article_.id, flags: Order.descending)
        .build();

    query.limit = limit;

    final articles = query.find();
    return articles;
  }

  List<Article> getArticles({int limit = 20}) {
    final query =
        articleBox.query().order(Article_.id, flags: Order.descending).build();

    query.limit = limit;
    final articles = query.find();
    return articles;
  }
}
