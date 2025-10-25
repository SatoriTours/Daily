import 'package:daily_satori/app/services/file_service.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api_utils/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api_utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 文章控制器
class ArticleController {
  /// 创建路由
  Router get router {
    final router = Router();

    // 应用认证中间件
    final pipeline = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    // 文章列表API
    router.get('/', pipeline.addHandler(_getArticles));

    // 文章搜索API
    router.get('/search', pipeline.addHandler(_searchArticles));

    // 获取单个文章API
    router.get('/<id>', _wrapHandler(_getArticle));

    // 创建文章API
    router.post('/', pipeline.addHandler(_createArticle));

    // 更新文章API
    router.put('/<id>', _wrapHandler(_updateArticle));

    // 删除文章API
    router.delete('/<id>', _wrapHandler(_deleteArticle));

    // 网页信息获取API
    router.post('/fetch-webpage', pipeline.addHandler(_fetchWebpage));

    return router;
  }

  /// 包装处理函数，处理带参数的路由
  Handler _wrapHandler(Function handler) {
    return (Request request) async {
      final params = request.url.pathSegments;
      final id = params.isNotEmpty ? params.last : null;
      if (id == null) {
        return ResponseUtils.validationError('无效的请求路径');
      }

      // 调用处理函数
      return await Function.apply(handler, [request, id]);
    };
  }

  /// 获取文章列表
  Future<Response> _getArticles(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final pageStr = params['page'] ?? '1';
      final page = int.tryParse(pageStr) ?? 1;

      // 获取指定页的文章
      final articles = ArticleRepository.d.allModelsPaginated(page: page, orderBy: Article_.id, descending: true);
      // 获取总页数和总条数
      final totalItems = ArticleRepository.d.count();
      final totalPages = ArticleRepository.d.totalPages();

      // 转换为JSON格式
      final articlesJson = articles.map(_articleToJson).toList();

      // 返回带分页信息的响应
      return ResponseUtils.success({
        'items': articlesJson,
        'pagination': {
          'page': page,
          'pageSize': ArticleRepository.d.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('获取文章列表失败: $e');
      return ResponseUtils.serverError('处理文章列表请求时发生错误');
    }
  }

  /// 搜索文章
  Future<Response> _searchArticles(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final query = params['q'] ?? '';
      final pageStr = params['page'] ?? '1';
      final page = int.tryParse(pageStr) ?? 1;

      if (query.isEmpty) {
        return ResponseUtils.validationError('搜索关键词不能为空');
      }

      // 搜索文章 - 使用分页方法
      final articles = ArticleRepository.d.queryArticlesPaginated(keyword: query, page: page);
      // 获取搜索结果的总数和总页数
      final totalItems = ArticleRepository.d.getSearchCount(query);
      final totalPages = ArticleRepository.d.getSearchTotalPages(query);

      // 转换为JSON格式
      final articlesJson = articles.map(_articleToJson).toList();

      // 返回带分页信息的响应
      return ResponseUtils.success({
        'items': articlesJson,
        'pagination': {
          'page': page,
          'pageSize': ArticleRepository.d.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('搜索文章失败: $e');
      return ResponseUtils.serverError('处理文章搜索请求时发生错误');
    }
  }

  /// 获取单个文章
  Future<Response> _getArticle(Request request, String id) async {
    try {
      final articleId = int.tryParse(id);
      if (articleId == null) {
        return ResponseUtils.validationError('无效的文章ID');
      }

      // 获取文章
      final article = ArticleRepository.d.findModel(articleId);
      if (article == null) {
        return ResponseUtils.error('文章不存在', status: 404);
      }

      // 转换为JSON格式
      final articleJson = _articleToJson(article);

      return ResponseUtils.success(articleJson);
    } catch (e) {
      logger.e('获取文章失败: $e');
      return ResponseUtils.serverError('处理获取文章请求时发生错误');
    }
  }

  /// 创建文章
  Future<Response> _createArticle(Request request) async {
    try {
      // 解析请求体
      final body = await RequestUtils.parseJsonBody(request);

      // 创建文章实体
      final article = Article(
        title: body['title'] as String?,
        content: body['content'] as String?,
        url: body['url'] as String?,
        isFavorite: body['isFavorite'] as bool? ?? false,
        comment: body['comment'] as String?,
        coverImage: body['coverImage'] as String?,
        createdAt: DateTime.now(),
        updatedAt: DateTime.now(),
      );

      // 创建文章模型
      final articleModel = ArticleModel(article);

      // 保存文章
      final articleId = await ArticleRepository.d.saveModel(articleModel);

      // 获取新创建的文章
      final newArticle = ArticleRepository.d.findModel(articleId);
      if (newArticle == null) {
        return ResponseUtils.serverError('文章创建失败');
      }

      // 转换为JSON格式
      final articleJson = _articleToJson(newArticle);

      return ResponseUtils.success(articleJson, status: 201);
    } catch (e) {
      logger.e('创建文章失败: $e');
      return ResponseUtils.serverError('处理创建文章请求时发生错误');
    }
  }

  /// 更新文章
  Future<Response> _updateArticle(Request request, String id) async {
    try {
      final articleId = int.tryParse(id);
      if (articleId == null) {
        return ResponseUtils.validationError('无效的文章ID');
      }

      // 获取现有文章
      final existingArticle = ArticleRepository.d.findModel(articleId);
      if (existingArticle == null) {
        return ResponseUtils.error('文章不存在', status: 404);
      }

      // 解析请求体
      final body = await RequestUtils.parseJsonBody(request);

      // 更新文章属性
      if (body.containsKey('title')) existingArticle.title = body['title'] as String?;
      if (body.containsKey('content')) existingArticle.content = body['content'] as String?;
      if (body.containsKey('url')) existingArticle.url = body['url'] as String?;
      if (body.containsKey('isFavorite')) existingArticle.isFavorite = body['isFavorite'] as bool;
      if (body.containsKey('comment')) existingArticle.comment = body['comment'] as String?;
      if (body.containsKey('coverImage')) existingArticle.coverImage = body['coverImage'] as String?;

      // 更新时间
      existingArticle.updatedAt = DateTime.now();

      // 保存更新 - 使用update方法
      await ArticleRepository.d.updateModel(existingArticle);

      // 转换为JSON格式
      final articleJson = _articleToJson(existingArticle);

      return ResponseUtils.success(articleJson);
    } catch (e) {
      logger.e('更新文章失败: $e');
      return ResponseUtils.serverError('处理更新文章请求时发生错误');
    }
  }

  /// 删除文章
  Future<Response> _deleteArticle(Request request, String id) async {
    try {
      final articleId = int.tryParse(id);
      if (articleId == null) {
        return ResponseUtils.validationError('无效的文章ID');
      }

      // 删除文章 - 使用destroy方法
      final success = ArticleRepository.d.remove(articleId);
      if (!success) {
        return ResponseUtils.error('文章不存在或删除失败', status: 404);
      }

      return ResponseUtils.success({'success': true});
    } catch (e) {
      logger.e('删除文章失败: $e');
      return ResponseUtils.serverError('处理删除文章请求时发生错误');
    }
  }

  /// 获取网页信息
  Future<Response> _fetchWebpage(Request request) async {
    try {
      // 解析请求体
      final body = await RequestUtils.parseJsonBody(request);

      // 验证必需字段
      if (!RequestUtils.validateRequiredFields(body, ['url'])) {
        return ResponseUtils.validationError('URL不能为空');
      }

      final url = body['url'] as String;

      // 这里应该调用网页解析服务，但为简化实现，返回一个模拟响应
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
      logger.e('获取网页信息失败: $e');
      return ResponseUtils.serverError('处理网页信息获取请求时发生错误');
    }
  }

  /// 将文章模型转换为JSON格式
  Map<String, dynamic> _articleToJson(ArticleModel article) {
    return {
      'id': article.id,
      'title': article.showTitle(),
      'content': article.showContent(),
      'aiMarkdownContent': article.aiMarkdownContent,
      'url': article.url,
      'isFavorite': article.isFavorite,
      'comment': article.comment,
      'status': article.status,
      'coverImage': FileService.i.convertLocalPathToWebPath(article.coverImage ?? ''),
      'coverImageUrl': article.coverImageUrl,
      'pubDate': article.pubDate?.toIso8601String(),
      'updatedAt': article.updatedAt.toIso8601String(),
      'createdAt': article.createdAt.toIso8601String(),
      'tags': article.tags.map((tag) => {'id': tag.id, 'name': tag.name}).toList(),
    };
  }
}
