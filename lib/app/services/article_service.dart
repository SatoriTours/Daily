import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

class ArticleService {
  // 单例模式
  ArticleService._();
  static final ArticleService _instance = ArticleService._();
  static ArticleService get i => _instance;

  // 数据库实例
  final _articleBox = ObjectboxService.i.box<Article>();

  // 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] ArticleService");
  }

  /// 创建文章模型
  ArticleModel createArticleModel(Map<String, dynamic> data) {
    final article = Article(
      title: data['title'],
      aiTitle: data['aiTitle'],
      content: data['content'],
      aiContent: data['aiContent'],
      htmlContent: data['htmlContent'],
      url: data['url'],
      pubDate: data['pubDate'],
      createdAt: data['createdAt'] ?? DateTime.now().toUtc(),
      updatedAt: data['updatedAt'] ?? DateTime.now().toUtc(),
      comment: data['comment'],
    );

    return ArticleModel(article);
  }

  /// 更新文章ID
  void updateArticleId(ArticleModel articleModel, int id) {
    articleModel.entity.id = id;
  }

  // 保存文章
  Future<Article?> saveArticle(Article article) async {
    try {
      article.id = _articleBox.put(article);
      logger.i("文章已保存: ${firstLine(article.title ?? '')}");
      return article;
    } catch (e) {
      logger.e("[保存文章失败] $e");
      return null;
    }
  }

  // 更新文章
  Future<Article?> updateArticle(int articleID, Article article) async {
    final existing = _articleBox.get(articleID);
    if (existing == null) {
      logger.i("未找到文章以更新: ${article.url}");
      return null;
    }

    article.id = articleID;
    article.id = _articleBox.put(article);
    logger.i("文章已更新: ${firstLine(article.title ?? '')}");
    return article;
  }

  // 根据URL获取第一篇文章
  Future<Article?> getFirstArticleByUrl(String url) async {
    return _articleBox.query(Article_.url.equals(url)).build().findFirst();
  }

  // 检查文章是否存在
  Future<bool> isArticleExists(String url) async {
    return await getFirstArticleByUrl(url) != null;
  }

  // 删除文章
  Future<void> deleteArticle(int articleID) async {
    final article = _articleBox.get(articleID);
    if (article == null) {
      logger.i("未找到文章以删除: $articleID");
      return;
    }

    // 清理关联数据
    article.tags.clear();
    article.images.clear();
    article.screenshots.clear();
    _articleBox.remove(articleID);
    logger.i("文章已删除: $articleID");
  }

  // 根据ID获取文章
  Future<Article?> getArticleById(int articleID) async {
    return _articleBox.get(articleID);
  }

  // 切换收藏状态
  Future<bool> toggleFavorite(int articleID) async {
    final article = await getArticleById(articleID);
    if (article == null) {
      logger.i("未找到文章以更新收藏状态: $articleID");
      return false;
    }

    article.isFavorite = !article.isFavorite;
    _articleBox.put(article);

    final status = article.isFavorite ? "已收藏" : "已取消收藏";
    logger.i("文章$status: $articleID");
    return article.isFavorite;
  }

  // 获取所有文章
  List<Article> getArticles({int limit = 20}) {
    return (_articleBox.query().order(Article_.id, flags: Order.descending).build()..limit = limit).find();
  }
}
