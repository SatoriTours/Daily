import 'dart:async';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api/middleware/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api/utils/markdown_image_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';

/// 日记 API 控制器
class DiaryController {
  Router get router {
    final router = Router();

    final authed = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    Future<Response> runAuthed(Request request, FutureOr<Response> Function(Request request) handler) async {
      final h = authed.addHandler(handler);
      return await h(request);
    }

    router.get('/', authed.addHandler(_getDiaries));
    router.get('/search', authed.addHandler(_searchDiaries));
    router.get('/<id>', (request, id) => runAuthed(request, (req) => _getDiary(req, id)));
    router.post('/', authed.addHandler(_createDiary));
    router.put('/<id>', (request, id) => runAuthed(request, (req) => _updateDiary(req, id)));
    router.delete('/<id>', (request, id) => runAuthed(request, (req) => _deleteDiary(req, id)));

    return router;
  }

  Future<Response> _getDiaries(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final page = int.tryParse(params['page'] ?? '1') ?? 1;

      final diaryRepository = DiaryRepository.i;
      final diaries = diaryRepository.findAllPaginated(page);
      final totalItems = diaryRepository.count();
      final totalPages = diaryRepository.totalPages();

      return ResponseUtils.success({
        'items': diaries.map(_diaryToJson).toList(),
        'pagination': {
          'page': page,
          'pageSize': DiaryRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('[WebService][Diary] 获取日记列表失败', error: e);
      return ResponseUtils.serverError('处理日记列表请求时发生错误');
    }
  }

  Future<Response> _searchDiaries(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final query = params['q'] ?? '';
      final page = int.tryParse(params['page'] ?? '1') ?? 1;

      if (query.isEmpty) return ResponseUtils.validationError('搜索关键词不能为空');

      final diaryRepository = DiaryRepository.i;
      final diaries = diaryRepository.findByContentPaginated(query, page);
      final totalItems = diaryRepository.getSearchCount(query);
      final totalPages = diaryRepository.getSearchTotalPages(query);

      return ResponseUtils.success({
        'items': diaries.map(_diaryToJson).toList(),
        'pagination': {
          'page': page,
          'pageSize': DiaryRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('[WebService][Diary] 搜索日记失败', error: e);
      return ResponseUtils.serverError('处理日记搜索请求时发生错误');
    }
  }

  Future<Response> _getDiary(Request request, String id) async {
    try {
      final diaryId = int.tryParse(id);
      if (diaryId == null) return ResponseUtils.validationError('无效的日记ID');

      final diaryRepository = DiaryRepository.i;
      final diary = diaryRepository.find(diaryId);
      if (diary == null) return ResponseUtils.error('日记不存在', status: 404);

      return ResponseUtils.success(_diaryToJson(diary));
    } catch (e) {
      logger.e('[WebService][Diary] 获取日记失败', error: e);
      return ResponseUtils.serverError('处理获取日记请求时发生错误');
    }
  }

  Future<Response> _createDiary(Request request) async {
    try {
      final body = await RequestUtils.parseJsonBody(request);
      if (!RequestUtils.validateRequiredFields(body, ['content'])) {
        return ResponseUtils.validationError('日记内容不能为空');
      }

      final diary = DiaryModel.create(
        content: body['content'] as String,
        tags: body['tags'] as String?,
        mood: body['mood'] as String?,
        images: body['images'] as String?,
      );

      final diaryRepository = DiaryRepository.i;
      final diaryId = diaryRepository.save(diary);

      final newDiary = diaryRepository.find(diaryId);
      if (newDiary == null) return ResponseUtils.serverError('日记创建失败');

      _refreshDiaryList();
      return ResponseUtils.success(_diaryToJson(newDiary), status: 201);
    } catch (e) {
      logger.e('[WebService][Diary] 创建日记失败', error: e);
      return ResponseUtils.serverError('处理创建日记请求时发生错误');
    }
  }

  Future<Response> _updateDiary(Request request, String id) async {
    try {
      final diaryId = int.tryParse(id);
      if (diaryId == null) return ResponseUtils.validationError('无效的日记ID');

      final diaryRepository = DiaryRepository.i;
      final existingDiary = diaryRepository.find(diaryId);
      if (existingDiary == null) return ResponseUtils.error('日记不存在', status: 404);

      final body = await RequestUtils.parseJsonBody(request);

      if (body.containsKey('content')) existingDiary.content = body['content'] as String;
      if (body.containsKey('tags')) existingDiary.tags = body['tags'] as String?;
      if (body.containsKey('mood')) existingDiary.mood = body['mood'] as String?;
      if (body.containsKey('images')) existingDiary.entity.images = body['images'] as String?;

      existingDiary.updatedAt = DateTime.now();
      diaryRepository.save(existingDiary);

      _refreshDiaryList();
      return ResponseUtils.success(_diaryToJson(existingDiary));
    } catch (e) {
      logger.e('[WebService][Diary] 更新日记失败', error: e);
      return ResponseUtils.serverError('处理更新日记请求时发生错误');
    }
  }

  Future<Response> _deleteDiary(Request request, String id) async {
    try {
      final diaryId = int.tryParse(id);
      if (diaryId == null) return ResponseUtils.validationError('无效的日记ID');

      final diaryRepository = DiaryRepository.i;
      final success = diaryRepository.delete(diaryId);
      if (!success) return ResponseUtils.error('日记不存在或删除失败', status: 404);

      _refreshDiaryList();
      return ResponseUtils.success({'success': true});
    } catch (e) {
      logger.e('[WebService][Diary] 删除日记失败', error: e);
      return ResponseUtils.serverError('处理删除日记请求时发生错误');
    }
  }

  Map<String, dynamic> _diaryToJson(DiaryModel diary) {
    return {
      'id': diary.id,
      'content': MarkdownImageUtils.convertContentImages(diary.content),
      'tags': diary.tags,
      'mood': diary.mood,
      'images': diary.imagesList.map(FileService.i.convertLocalPathToWebPath).toList(),
      'createdAt': diary.createdAt.toIso8601String(),
      'updatedAt': diary.updatedAt.toIso8601String(),
    };
  }

  void _refreshDiaryList() {
    // Web API is stateless, UI will refresh via provider
    logger.d('Diary list updated via web API');
  }
}
