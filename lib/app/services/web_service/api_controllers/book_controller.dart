import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api_utils/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api_utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/session_manager.dart';

/// 书籍控制器
class BookController {
  /// 创建路由
  Router get router {
    final router = Router();

    // 应用认证中间件
    final pipeline = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    // 书籍列表API
    router.get('/', pipeline.addHandler(_getBooks));

    // 获取单个书籍API（包含观点列表）
    router.get('/<id>', _wrapHandler(_getBook));

    // 创建书籍API
    router.post('/', pipeline.addHandler(_createBook));

    // 更新书籍API
    router.put('/<id>', _wrapHandler(_updateBook));

    // 删除书籍API
    router.delete('/<id>', _wrapHandler(_deleteBook));

    // 书籍观点API
    router.get('/<id>/viewpoints', _wrapHandler(_getViewpoints));
    router.post('/<id>/viewpoints', _wrapHandler(_createViewpoint));
    router.put('/<id>/viewpoints/<viewpointId>', _wrapViewpointHandler(_updateViewpoint));
    router.delete('/<id>/viewpoints/<viewpointId>', _wrapViewpointHandler(_deleteViewpoint));

    return router;
  }

  /// 包装处理函数，处理带参数的路由
  Handler _wrapHandler(Function handler) {
    return (Request request) async {
      final params = request.url.pathSegments;
      final id = params.isNotEmpty ? params.first : null;
      if (id == null) {
        return ResponseUtils.validationError('无效的请求路径');
      }

      // 验证认证
      final authResult = await _checkAuth(request);
      if (authResult != null) return authResult;

      return await Function.apply(handler, [request, id]);
    };
  }

  /// 包装观点处理函数
  Handler _wrapViewpointHandler(Function handler) {
    return (Request request) async {
      final params = request.url.pathSegments;
      if (params.length < 3) {
        return ResponseUtils.validationError('无效的请求路径');
      }
      final bookId = params[0];
      final viewpointId = params[2];

      // 验证认证
      final authResult = await _checkAuth(request);
      if (authResult != null) return authResult;

      return await Function.apply(handler, [request, bookId, viewpointId]);
    };
  }

  /// 检查认证
  Future<Response?> _checkAuth(Request request) async {
    final sessionId = RequestUtils.getSessionId(request);
    if (sessionId == null) {
      return ResponseUtils.unauthorized('未登录或会话已过期');
    }

    final session = await SessionManager.getSession(sessionId);
    if (session == null || !session.isAuthenticated) {
      return ResponseUtils.unauthorized('未登录或会话已过期');
    }
    return null;
  }

  /// 获取书籍列表
  Future<Response> _getBooks(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final pageStr = params['page'] ?? '1';
      final page = int.tryParse(pageStr) ?? 1;

      // 获取书籍列表
      final books = BookRepository.i.allPaginated(page: page);
      final totalItems = BookRepository.i.count();
      final totalPages = BookRepository.i.totalPages();

      // 转换为JSON格式
      final booksJson = books.map(_bookToJson).toList();

      return ResponseUtils.success({
        'items': booksJson,
        'pagination': {
          'page': page,
          'pageSize': BookRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('获取书籍列表失败: $e');
      return ResponseUtils.serverError('处理书籍列表请求时发生错误');
    }
  }

  /// 获取单个书籍（包含观点列表）
  Future<Response> _getBook(Request request, String id) async {
    try {
      final bookId = int.tryParse(id);
      if (bookId == null) {
        return ResponseUtils.validationError('无效的书籍ID');
      }

      final book = BookRepository.i.find(bookId);
      if (book == null) {
        return ResponseUtils.error('书籍不存在', status: 404);
      }

      // 获取书籍观点
      final viewpoints = BookViewpointRepository.i.findModelsByBookIds([bookId]);
      final viewpointsJson = viewpoints.map(_viewpointToJson).toList();

      final bookJson = _bookToJson(book);
      bookJson['viewpoints'] = viewpointsJson;

      return ResponseUtils.success(bookJson);
    } catch (e) {
      logger.e('获取书籍失败: $e');
      return ResponseUtils.serverError('处理获取书籍请求时发生错误');
    }
  }

  /// 创建书籍
  Future<Response> _createBook(Request request) async {
    try {
      final body = await RequestUtils.parseJsonBody(request);

      final title = body['title'] as String?;
      final author = body['author'] as String? ?? '';
      final category = body['category'] as String? ?? '';
      final introduction = body['introduction'] as String? ?? '';
      final coverImage = body['coverImage'] as String? ?? '';

      if (title == null || title.isEmpty) {
        return ResponseUtils.validationError('书名不能为空');
      }

      final book = BookModel.create(
        title: title,
        author: author,
        category: category,
        introduction: introduction,
        coverImage: coverImage,
      );

      BookRepository.i.save(book);

      return ResponseUtils.success(_bookToJson(book));
    } catch (e) {
      logger.e('创建书籍失败: $e');
      return ResponseUtils.serverError('创建书籍时发生错误');
    }
  }

  /// 更新书籍
  Future<Response> _updateBook(Request request, String id) async {
    try {
      final bookId = int.tryParse(id);
      if (bookId == null) {
        return ResponseUtils.validationError('无效的书籍ID');
      }

      final book = BookRepository.i.find(bookId);
      if (book == null) {
        return ResponseUtils.error('书籍不存在', status: 404);
      }

      final body = await RequestUtils.parseJsonBody(request);

      if (body['title'] != null) book.title = body['title'] as String;
      if (body['author'] != null) book.author = body['author'] as String;
      if (body['category'] != null) book.category = body['category'] as String;
      if (body['introduction'] != null) book.introduction = body['introduction'] as String;
      if (body['coverImage'] != null) book.coverImage = body['coverImage'] as String;

      BookRepository.i.save(book);

      return ResponseUtils.success(_bookToJson(book));
    } catch (e) {
      logger.e('更新书籍失败: $e');
      return ResponseUtils.serverError('更新书籍时发生错误');
    }
  }

  /// 删除书籍
  Future<Response> _deleteBook(Request request, String id) async {
    try {
      final bookId = int.tryParse(id);
      if (bookId == null) {
        return ResponseUtils.validationError('无效的书籍ID');
      }

      final book = BookRepository.i.find(bookId);
      if (book == null) {
        return ResponseUtils.error('书籍不存在', status: 404);
      }

      // 删除关联的观点
      final viewpoints = BookViewpointRepository.i.findModelsByBookIds([bookId]);
      if (viewpoints.isNotEmpty) {
        BookViewpointRepository.i.removeMany(viewpoints.map((v) => v.id).toList());
      }

      BookRepository.i.remove(bookId);

      return ResponseUtils.success({'deleted': true});
    } catch (e) {
      logger.e('删除书籍失败: $e');
      return ResponseUtils.serverError('删除书籍时发生错误');
    }
  }

  /// 获取书籍观点列表
  Future<Response> _getViewpoints(Request request, String bookId) async {
    try {
      final id = int.tryParse(bookId);
      if (id == null) {
        return ResponseUtils.validationError('无效的书籍ID');
      }

      final viewpoints = BookViewpointRepository.i.findModelsByBookIds([id]);
      final viewpointsJson = viewpoints.map(_viewpointToJson).toList();

      return ResponseUtils.success(viewpointsJson);
    } catch (e) {
      logger.e('获取观点列表失败: $e');
      return ResponseUtils.serverError('获取观点列表时发生错误');
    }
  }

  /// 创建观点
  Future<Response> _createViewpoint(Request request, String bookId) async {
    try {
      final id = int.tryParse(bookId);
      if (id == null) {
        return ResponseUtils.validationError('无效的书籍ID');
      }

      final book = BookRepository.i.find(id);
      if (book == null) {
        return ResponseUtils.error('书籍不存在', status: 404);
      }

      final body = await RequestUtils.parseJsonBody(request);

      final title = body['title'] as String? ?? '';
      final content = body['content'] as String?;
      final example = body['example'] as String? ?? '';

      if (content == null || content.isEmpty) {
        return ResponseUtils.validationError('观点内容不能为空');
      }

      final viewpoint = BookViewpointModel.create(bookId: id, title: title, content: content, example: example);

      BookViewpointRepository.i.save(viewpoint);

      return ResponseUtils.success(_viewpointToJson(viewpoint));
    } catch (e) {
      logger.e('创建观点失败: $e');
      return ResponseUtils.serverError('创建观点时发生错误');
    }
  }

  /// 更新观点
  Future<Response> _updateViewpoint(Request request, String bookId, String viewpointId) async {
    try {
      final vpId = int.tryParse(viewpointId);
      if (vpId == null) {
        return ResponseUtils.validationError('无效的观点ID');
      }

      final viewpoint = BookViewpointRepository.i.find(vpId);
      if (viewpoint == null) {
        return ResponseUtils.error('观点不存在', status: 404);
      }

      final body = await RequestUtils.parseJsonBody(request);

      if (body['title'] != null) viewpoint.title = body['title'] as String;
      if (body['content'] != null) viewpoint.content = body['content'] as String;
      if (body['example'] != null) viewpoint.example = body['example'] as String;

      BookViewpointRepository.i.save(viewpoint);

      return ResponseUtils.success(_viewpointToJson(viewpoint));
    } catch (e) {
      logger.e('更新观点失败: $e');
      return ResponseUtils.serverError('更新观点时发生错误');
    }
  }

  /// 删除观点
  Future<Response> _deleteViewpoint(Request request, String bookId, String viewpointId) async {
    try {
      final vpId = int.tryParse(viewpointId);
      if (vpId == null) {
        return ResponseUtils.validationError('无效的观点ID');
      }

      final viewpoint = BookViewpointRepository.i.find(vpId);
      if (viewpoint == null) {
        return ResponseUtils.error('观点不存在', status: 404);
      }

      BookViewpointRepository.i.remove(vpId);

      return ResponseUtils.success({'deleted': true});
    } catch (e) {
      logger.e('删除观点失败: $e');
      return ResponseUtils.serverError('删除观点时发生错误');
    }
  }

  /// 将书籍转换为JSON
  Map<String, dynamic> _bookToJson(BookModel book) {
    return {
      'id': book.id,
      'title': book.title,
      'author': book.author,
      'category': book.category,
      'coverImage': book.coverImage,
      'introduction': book.introduction,
      'createdAt': book.createdAt.toIso8601String(),
      'updatedAt': book.updatedAt.toIso8601String(),
    };
  }

  /// 将观点转换为JSON
  Map<String, dynamic> _viewpointToJson(BookViewpointModel viewpoint) {
    return {
      'id': viewpoint.id,
      'bookId': viewpoint.bookId,
      'title': viewpoint.title,
      'content': viewpoint.content,
      'example': viewpoint.example,
      'createdAt': viewpoint.createdAt.toIso8601String(),
      'updatedAt': viewpoint.updatedAt.toIso8601String(),
    };
  }
}
