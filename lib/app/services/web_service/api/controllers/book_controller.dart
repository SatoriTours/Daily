import 'dart:async';

import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api/middleware/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api/utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';

/// 书籍 API 控制器
class BookController {
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

    router.get('/', authed.addHandler(_getBooks));

    // 书籍本体
    router.get(
      '/<id>',
      (request, id) => runAuthed(request, (req) => _getBook(req, id)),
    );
    router.post('/', authed.addHandler(_createBook));
    router.put(
      '/<id>',
      (request, id) => runAuthed(request, (req) => _updateBook(req, id)),
    );
    router.delete(
      '/<id>',
      (request, id) => runAuthed(request, (req) => _deleteBook(req, id)),
    );

    // 观点
    router.get(
      '/<id>/viewpoints',
      (request, id) => runAuthed(request, (req) => _getViewpoints(req, id)),
    );
    router.post(
      '/<id>/viewpoints',
      (request, id) => runAuthed(request, (req) => _createViewpoint(req, id)),
    );
    router.put(
      '/<id>/viewpoints/<viewpointId>',
      (request, id, viewpointId) =>
          runAuthed(request, (req) => _updateViewpoint(req, id, viewpointId)),
    );
    router.delete(
      '/<id>/viewpoints/<viewpointId>',
      (request, id, viewpointId) =>
          runAuthed(request, (req) => _deleteViewpoint(req, id, viewpointId)),
    );

    return router;
  }

  Future<Response> _getBooks(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final page = int.tryParse(params['page'] ?? '1') ?? 1;

      final books = BookRepository.i.allPaginated(page: page);
      final totalItems = BookRepository.i.count();
      final totalPages = BookRepository.i.totalPages();

      return ResponseUtils.success({
        'items': books.map(_bookToJson).toList(),
        'pagination': {
          'page': page,
          'pageSize': BookRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('[WebService][Books] 获取书籍列表失败', error: e);
      return ResponseUtils.serverError('处理书籍列表请求时发生错误');
    }
  }

  Future<Response> _getBook(Request request, String id) async {
    try {
      final bookId = int.tryParse(id);
      if (bookId == null) return ResponseUtils.validationError('无效的书籍ID');

      final book = BookRepository.i.find(bookId);
      if (book == null) return ResponseUtils.error('书籍不存在', status: 404);

      final viewpoints = BookViewpointRepository.i.findModelsByBookIds([
        bookId,
      ]);
      final bookJson = _bookToJson(book);
      bookJson['viewpoints'] = viewpoints.map(_viewpointToJson).toList();

      return ResponseUtils.success(bookJson);
    } catch (e) {
      logger.e('[WebService][Books] 获取书籍失败', error: e);
      return ResponseUtils.serverError('处理获取书籍请求时发生错误');
    }
  }

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
      logger.e('[WebService][Books] 创建书籍失败', error: e);
      return ResponseUtils.serverError('创建书籍时发生错误');
    }
  }

  Future<Response> _updateBook(Request request, String id) async {
    try {
      final bookId = int.tryParse(id);
      if (bookId == null) return ResponseUtils.validationError('无效的书籍ID');

      final book = BookRepository.i.find(bookId);
      if (book == null) return ResponseUtils.error('书籍不存在', status: 404);

      final body = await RequestUtils.parseJsonBody(request);

      if (body['title'] != null) book.title = body['title'] as String;
      if (body['author'] != null) book.author = body['author'] as String;
      if (body['category'] != null) book.category = body['category'] as String;
      if (body['introduction'] != null) {
        book.introduction = body['introduction'] as String;
      }
      if (body['coverImage'] != null) {
        book.coverImage = body['coverImage'] as String;
      }

      BookRepository.i.save(book);
      return ResponseUtils.success(_bookToJson(book));
    } catch (e) {
      logger.e('[WebService][Books] 更新书籍失败', error: e);
      return ResponseUtils.serverError('更新书籍时发生错误');
    }
  }

  Future<Response> _deleteBook(Request request, String id) async {
    try {
      final bookId = int.tryParse(id);
      if (bookId == null) return ResponseUtils.validationError('无效的书籍ID');

      final book = BookRepository.i.find(bookId);
      if (book == null) return ResponseUtils.error('书籍不存在', status: 404);

      final viewpoints = BookViewpointRepository.i.findModelsByBookIds([
        bookId,
      ]);
      if (viewpoints.isNotEmpty) {
        BookViewpointRepository.i.removeMany(
          viewpoints.map((v) => v.id).toList(),
        );
      }

      BookRepository.i.remove(bookId);
      return ResponseUtils.success({'deleted': true});
    } catch (e) {
      logger.e('[WebService][Books] 删除书籍失败', error: e);
      return ResponseUtils.serverError('删除书籍时发生错误');
    }
  }

  Future<Response> _getViewpoints(Request request, String bookId) async {
    try {
      final id = int.tryParse(bookId);
      if (id == null) return ResponseUtils.validationError('无效的书籍ID');

      final viewpoints = BookViewpointRepository.i.findModelsByBookIds([id]);
      return ResponseUtils.success(viewpoints.map(_viewpointToJson).toList());
    } catch (e) {
      logger.e('[WebService][Books] 获取观点列表失败', error: e);
      return ResponseUtils.serverError('获取观点列表时发生错误');
    }
  }

  Future<Response> _createViewpoint(Request request, String bookId) async {
    try {
      final id = int.tryParse(bookId);
      if (id == null) return ResponseUtils.validationError('无效的书籍ID');

      final book = BookRepository.i.find(id);
      if (book == null) return ResponseUtils.error('书籍不存在', status: 404);

      final body = await RequestUtils.parseJsonBody(request);

      final title = body['title'] as String? ?? '';
      final content = body['content'] as String?;
      final example = body['example'] as String? ?? '';

      if (content == null || content.isEmpty) {
        return ResponseUtils.validationError('观点内容不能为空');
      }

      final viewpoint = BookViewpointModel.create(
        bookId: id,
        title: title,
        content: content,
        example: example,
      );

      BookViewpointRepository.i.save(viewpoint);
      return ResponseUtils.success(_viewpointToJson(viewpoint));
    } catch (e) {
      logger.e('[WebService][Books] 创建观点失败', error: e);
      return ResponseUtils.serverError('创建观点时发生错误');
    }
  }

  Future<Response> _updateViewpoint(
    Request request,
    String bookId,
    String viewpointId,
  ) async {
    try {
      final vpId = int.tryParse(viewpointId);
      if (vpId == null) return ResponseUtils.validationError('无效的观点ID');

      final viewpoint = BookViewpointRepository.i.find(vpId);
      if (viewpoint == null) return ResponseUtils.error('观点不存在', status: 404);

      final body = await RequestUtils.parseJsonBody(request);

      if (body['title'] != null) viewpoint.title = body['title'] as String;
      if (body['content'] != null) {
        viewpoint.content = body['content'] as String;
      }
      if (body['example'] != null) {
        viewpoint.example = body['example'] as String;
      }

      BookViewpointRepository.i.save(viewpoint);
      return ResponseUtils.success(_viewpointToJson(viewpoint));
    } catch (e) {
      logger.e('[WebService][Books] 更新观点失败', error: e);
      return ResponseUtils.serverError('更新观点时发生错误');
    }
  }

  Future<Response> _deleteViewpoint(
    Request request,
    String bookId,
    String viewpointId,
  ) async {
    try {
      final vpId = int.tryParse(viewpointId);
      if (vpId == null) return ResponseUtils.validationError('无效的观点ID');

      final viewpoint = BookViewpointRepository.i.find(vpId);
      if (viewpoint == null) return ResponseUtils.error('观点不存在', status: 404);

      BookViewpointRepository.i.remove(vpId);
      return ResponseUtils.success({'deleted': true});
    } catch (e) {
      logger.e('[WebService][Books] 删除观点失败', error: e);
      return ResponseUtils.serverError('删除观点时发生错误');
    }
  }

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
