import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/global.dart';
import 'package:sqflite/sqflite.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get instance => _instance;

  Future<void> init() async {}

  final String _tableName = 'articles';

  Future<void> saveArticle(Map<String, dynamic> articleData) async {
    if (await articleExists(articleData['url'])) {
      logger.i("文章已存在: ${firstLine(articleData['title'])}");
      return; // 如果记录已存在，则不进行插入
    }

    await db.insert(_tableName, articleData);
    logger.i("文章已保存: ${firstLine(articleData['title'])}");
  }

  Future<void> updateArticle(String url, Map<String, dynamic> newArticleData) async {
    final result = await db.update(
      _tableName,
      newArticleData,
      where: 'url = ?',
      whereArgs: [url],
    );

    if (result > 0) {
      logger.i("文章已更新: ${firstLine(newArticleData['title'])}");
    } else {
      logger.i("未找到文章以更新: $url");
    }
  }

  Future<bool> articleExists(String url) async {
    final existingArticle = await db.query(
      _tableName,
      where: 'url = ?',
      whereArgs: [url],
    );
    return existingArticle.isNotEmpty;
  }

  Future<void> deleteArticle(int articleID) async {
    final result = await db.delete(
      _tableName,
      where: 'id = ?',
      whereArgs: [articleID],
    );

    if (result > 0) {
      logger.i("文章已删除: $articleID");
    } else {
      logger.i("未找到文章以删除: $articleID");
    }
  }

  Future<Article?> getArticleById(int articleID) async {
    final List<Map<String, dynamic>> maps = await db.query(
      _tableName,
      where: 'id = ?',
      whereArgs: [articleID],
    );

    if (maps.isNotEmpty) {
      return Article.fromMap(maps.first);
    } else {
      return null; // 返回 null 表示未找到文章
    }
  }

  Future<bool> toggleFavorite(int articleID) async {
    final article = await getArticleById(articleID);
    if (article != null) {
      final newFavoriteStatus = article.isFavorite == 0 ? 1 : 0; // 切换收藏状态
      final result = await db.update(
        _tableName,
        {'is_favorite': newFavoriteStatus},
        where: 'id = ?',
        whereArgs: [articleID],
      );

      if (result > 0) {
        logger.i(newFavoriteStatus == 1 ? "文章已收藏: $articleID" : "文章已取消收藏: $articleID");
        return newFavoriteStatus == 1; // 返回是否收藏
      } else {
        logger.i("未找到文章以更新收藏状态: $articleID");
      }
    }
    return false; // 如果文章不存在，返回未收藏
  }

  Database get db => DatabaseService.instance.database;
}

class Article {
  final int? id;
  final String title;
  final String? aiTitle;
  final String content;
  final String? aiContent;
  final String? htmlContent;
  final String url;
  final String? imageUrl;
  final String? imagePath;
  final String? screenshotPath;
  final int isRead;
  final int isFavorite;
  final DateTime? pubDate;
  final String? comment;
  final int? tagId;
  final DateTime createdAt;
  final DateTime updatedAt;

  Article({
    this.id,
    required this.title,
    this.aiTitle,
    required this.content,
    this.aiContent,
    this.htmlContent,
    required this.url,
    this.imageUrl,
    this.imagePath,
    this.screenshotPath,
    this.isRead = 0,
    this.isFavorite = 0,
    this.pubDate,
    this.comment,
    this.tagId,
    DateTime? createdAt,
    DateTime? updatedAt,
  })  : createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now();

  factory Article.fromMap(Map<String, dynamic> map) {
    return Article(
      id: map['id'] as int?,
      title: map['title'] as String,
      aiTitle: map['ai_title'] as String?,
      content: map['content'] as String,
      aiContent: map['ai_content'] as String?,
      htmlContent: map['html_content'] as String?,
      url: map['url'] as String,
      imageUrl: map['image_url'] as String?,
      imagePath: map['image_path'] as String?,
      screenshotPath: map['screenshot_path'] as String?,
      isRead: map['is_read'] as int? ?? 0,
      isFavorite: map['is_favorite'] as int? ?? 0,
      pubDate: map['pub_date'] != null ? DateTime.tryParse(map['pub_date']) : null,
      comment: map['comment'] as String?,
      tagId: map['tag_id'] as int?,
      createdAt: DateTime.parse(map['created_at']),
      updatedAt: DateTime.parse(map['updated_at']),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'ai_title': aiTitle,
      'content': content,
      'ai_content': aiContent,
      'html_content': htmlContent,
      'url': url,
      'image_url': imageUrl,
      'image_path': imagePath,
      'screenshot_path': screenshotPath,
      'is_read': isRead,
      'is_favorite': isFavorite,
      'pub_date': pubDate?.toIso8601String(),
      'comment': comment,
      'tag_id': tagId,
      'created_at': createdAt.toIso8601String(),
      'updated_at': updatedAt.toIso8601String(),
    };
  }
}
