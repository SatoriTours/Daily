import 'dart:async';

import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api/middleware/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api/utils/markdown_image_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';
import 'package:daily_satori/objectbox.g.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';

/// 文章 API 控制器
class ArticleController {
  Router get router {
    final router = Router();

    final authed = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    Future<Response> runAuthed(
      Request request,
      FutureOr<Response> Function(Request request) handler,
    ) async {
      final h = authed.addHandler(handler);
      return await h(request);
    }

    router.get('/', authed.addHandler(_getArticles));
    router.get('/search', authed.addHandler(_searchArticles));

    router.get(
      '/<id>',
      (request, id) => runAuthed(request, (req) => _getArticle(req, id)),
    );
    router.put(
      '/<id>',
      (request, id) => runAuthed(request, (req) => _updateArticle(req, id)),
    );
    router.delete(
      '/<id>',
      (request, id) => runAuthed(request, (req) => _deleteArticle(req, id)),
    );

    router.post('/', authed.addHandler(_createArticle));
    router.post('/fetch-webpage', authed.addHandler(_fetchWebpage));

    return router;
  }

  Future<Response> _getArticles(Request request) async {
    try {
      final page = _parsePage(request);

      final articles = ArticleRepository.i.allModelsPaginated(
        page: page,
        orderBy: Article_.id,
        descending: true,
      );
      final totalItems = ArticleRepository.i.count();
      final totalPages = ArticleRepository.i.totalPages();

      return ResponseUtils.success({
        'items': articles.map(_articleToJson).toList(),
        'pagination': {
          'page': page,
          'pageSize': ArticleRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('[WebService][Articles] 获取文章列表失败', error: e);
      return ResponseUtils.serverError('处理文章列表请求时发生错误');
    }
  }

  Future<Response> _searchArticles(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final query = params['q'] ?? '';
      final page = int.tryParse(params['page'] ?? '1') ?? 1;

      if (query.isEmpty) return ResponseUtils.validationError('搜索关键词不能为空');

      final articles = ArticleRepository.i.findArticlesPaginated(
        keyword: query,
        page: page,
      );
      final totalItems = ArticleRepository.i.getSearchCount(query);
      final totalPages = ArticleRepository.i.getSearchTotalPages(query);

      return ResponseUtils.success({
        'items': articles.map(_articleToJson).toList(),
        'pagination': {
          'page': page,
          'pageSize': ArticleRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('[WebService][Articles] 搜索文章失败', error: e);
      return ResponseUtils.serverError('处理文章搜索请求时发生错误');
    }
  }

  Future<Response> _getArticle(Request request, String id) async {
    try {
      final articleId = int.tryParse(id);
      if (articleId == null) return ResponseUtils.validationError('无效的文章ID');

      final article = ArticleRepository.i.findModel(articleId);
      if (article == null) return ResponseUtils.error('文章不存在', status: 404);

      return ResponseUtils.success(_articleToJson(article));
    } catch (e) {
      logger.e('[WebService][Articles] 获取文章失败', error: e);
      return ResponseUtils.serverError('处理获取文章请求时发生错误');
    }
  }

  Future<Response> _createArticle(Request request) async {
    try {
      final body = await RequestUtils.parseJsonBody(request);
      final url = body['url'] as String?;
      final comment = body['comment'] as String? ?? '';

      if (url == null || url.isEmpty)
        return ResponseUtils.validationError('URL不能为空');

      logger.i('[WebService][Articles] 创建文章: $url');

      final article = await WebpageParserService.i.saveWebpage(
        url: url,
        comment: comment,
        isUpdate: false,
        articleID: 0,
      );

      logger.i('[WebService][Articles] 创建文章成功: ID=${article.id}');
      return ResponseUtils.success(_articleToJson(article), status: 201);
    } catch (e) {
      logger.e('[WebService][Articles] 创建文章失败', error: e);
      return ResponseUtils.serverError('创建文章失败: $e');
    }
  }

  Future<Response> _updateArticle(Request request, String id) async {
    try {
      final articleId = int.tryParse(id);
      if (articleId == null) return ResponseUtils.validationError('无效的文章ID');

      final existing = ArticleRepository.i.findModel(articleId);
      if (existing == null) return ResponseUtils.error('文章不存在', status: 404);

      final body = await RequestUtils.parseJsonBody(request);
      _applyArticlePatch(existing, body);

      existing.updatedAt = DateTime.now();
      ArticleRepository.i.updateModel(existing);

      return ResponseUtils.success(_articleToJson(existing));
    } catch (e) {
      logger.e('[WebService][Articles] 更新文章失败', error: e);
      return ResponseUtils.serverError('处理更新文章请求时发生错误');
    }
  }

  Future<Response> _deleteArticle(Request request, String id) async {
    try {
      final articleId = int.tryParse(id);
      if (articleId == null) return ResponseUtils.validationError('无效的文章ID');

      final success = ArticleRepository.i.remove(articleId);
      if (!success) return ResponseUtils.error('文章不存在或删除失败', status: 404);

      return ResponseUtils.success({'success': true});
    } catch (e) {
      logger.e('[WebService][Articles] 删除文章失败', error: e);
      return ResponseUtils.serverError('处理删除文章请求时发生错误');
    }
  }

  Future<Response> _fetchWebpage(Request request) async {
    try {
      final body = await RequestUtils.parseJsonBody(request);
      if (!RequestUtils.validateRequiredFields(body, ['url'])) {
        return ResponseUtils.validationError('URL不能为空');
      }

      final url = body['url'] as String;

      // 保持现状：这里仅返回模拟响应。
      return ResponseUtils.success({
        'id': 0,
        'url': url,
        'title': '从URL获取的标题',
        'summary': '从URL获取的摘要',
        'favicon': '',
        'screenshot': '',
        'addedAt': DateTime.now().toIso8601String(),
      });
    } catch (e) {
      logger.e('[WebService][Articles] 获取网页信息失败', error: e);
      return ResponseUtils.serverError('处理网页信息获取请求时发生错误');
    }
  }

  int _parsePage(Request request) {
    final params = RequestUtils.parseQueryParams(request);
    return int.tryParse(params['page'] ?? '1') ?? 1;
  }

  void _applyArticlePatch(ArticleModel article, Map<String, dynamic> body) {
    if (body.containsKey('title')) article.title = body['title'] as String?;
    if (body.containsKey('content'))
      article.content = body['content'] as String?;
    if (body.containsKey('url')) article.url = body['url'] as String?;
    if (body.containsKey('isFavorite'))
      article.isFavorite = body['isFavorite'] as bool;
    if (body.containsKey('comment'))
      article.comment = body['comment'] as String?;
    if (body.containsKey('coverImage'))
      article.coverImage = body['coverImage'] as String?;
  }

  Map<String, dynamic> _articleToJson(ArticleModel article) {
    final content = MarkdownImageUtils.convertContentImages(
      article.showContent(),
    );
    final aiMarkdown = MarkdownImageUtils.convertContentImages(
      article.aiMarkdownContent ?? '',
    );

    return {
      'id': article.id,
      'title': article.showTitle(),
      'content': content,
      'aiMarkdownContent': aiMarkdown,
      'url': article.url,
      'isFavorite': article.isFavorite,
      'comment': article.comment,
      'status': article.status.value,
      'coverImage': FileService.i.convertLocalPathToWebPath(
        article.coverImage ?? '',
      ),
      'coverImageUrl': article.coverImageUrl,
      'pubDate': article.pubDate?.toIso8601String(),
      'updatedAt': article.updatedAt.toIso8601String(),
      'createdAt': article.createdAt.toIso8601String(),
      'tags': article.tags
          .map((tag) => {'id': tag.id, 'name': tag.name})
          .toList(),
    };
  }
}
