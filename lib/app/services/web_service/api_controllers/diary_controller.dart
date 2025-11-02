import 'package:daily_satori/app/services/file_service.dart';
import 'package:get/get.dart' as getx;
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/repositories/diary_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api_utils/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api_utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/session_manager.dart';
import 'package:daily_satori/app/modules/diary/controllers/diary_controller.dart' as app_controller;

/// 日记控制器
class DiaryController {
  /// 创建路由
  Router get router {
    final router = Router();

    // 应用认证中间件
    final pipeline = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

    // 日记列表API
    router.get('/', pipeline.addHandler(_getDiaries));

    // 日记搜索API
    router.get('/search', pipeline.addHandler(_searchDiaries));

    // 获取单个日记API
    router.get('/<id>', _wrapHandler(_getDiary));

    // 创建日记API
    router.post('/', pipeline.addHandler(_createDiary));

    // 更新日记API
    router.put('/<id>', _wrapHandler(_updateDiary));

    // 删除日记API
    router.delete('/<id>', _wrapHandler(_deleteDiary));

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

      // 验证会话
      final sessionId = RequestUtils.getSessionId(request);
      if (sessionId == null) {
        return ResponseUtils.unauthorized('未登录或会话已过期');
      }

      final session = await SessionManager.getSession(sessionId);
      if (session == null || !session.isAuthenticated) {
        return ResponseUtils.unauthorized('未登录或会话已过期');
      }

      // 调用处理函数
      return await Function.apply(handler, [request, id]);
    };
  }

  /// 获取日记列表
  Future<Response> _getDiaries(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final pageStr = params['page'] ?? '1';
      final page = int.tryParse(pageStr) ?? 1;

      // 获取指定页的日记
      final diaryRepository = DiaryRepository.i;
      final diaries = diaryRepository.getAllPaginated(page);
      // 获取总页数和总条数
      final totalItems = diaryRepository.getTotalCount();
      final totalPages = diaryRepository.getTotalPages();

      // 转换为JSON格式
      final diariesJson = diaries.map(_diaryToJson).toList();

      // 返回带分页信息的响应
      return ResponseUtils.success({
        'items': diariesJson,
        'pagination': {
          'page': page,
          'pageSize': DiaryRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('获取日记列表失败: $e');
      return ResponseUtils.serverError('处理日记列表请求时发生错误');
    }
  }

  /// 搜索日记
  Future<Response> _searchDiaries(Request request) async {
    try {
      final params = RequestUtils.parseQueryParams(request);
      final query = params['q'] ?? '';
      final pageStr = params['page'] ?? '1';
      final page = int.tryParse(pageStr) ?? 1;

      if (query.isEmpty) {
        return ResponseUtils.validationError('搜索关键词不能为空');
      }

      // 搜索日记内容，使用分页方法
      final diaryRepository = DiaryRepository.i;
      final diaries = diaryRepository.searchByContentPaginated(query, page);
      // 获取搜索结果的总数和总页数
      final totalItems = diaryRepository.getSearchCount(query);
      final totalPages = diaryRepository.getSearchTotalPages(query);

      // 转换为JSON格式
      final diariesJson = diaries.map(_diaryToJson).toList();

      // 返回带分页信息的响应
      return ResponseUtils.success({
        'items': diariesJson,
        'pagination': {
          'page': page,
          'pageSize': DiaryRepository.i.pageSize,
          'totalItems': totalItems,
          'totalPages': totalPages,
        },
      });
    } catch (e) {
      logger.e('搜索日记失败: $e');
      return ResponseUtils.serverError('处理日记搜索请求时发生错误');
    }
  }

  /// 获取单个日记
  Future<Response> _getDiary(Request request, String id) async {
    try {
      final diaryId = int.tryParse(id);
      if (diaryId == null) {
        return ResponseUtils.validationError('无效的日记ID');
      }

      // 获取日记
      final diaryRepository = DiaryRepository.i;
      final diary = diaryRepository.getById(diaryId);
      if (diary == null) {
        return ResponseUtils.error('日记不存在', status: 404);
      }

      // 转换为JSON格式
      final diaryJson = _diaryToJson(diary);

      return ResponseUtils.success(diaryJson);
    } catch (e) {
      logger.e('获取日记失败: $e');
      return ResponseUtils.serverError('处理获取日记请求时发生错误');
    }
  }

  /// 创建日记
  Future<Response> _createDiary(Request request) async {
    try {
      // 解析请求体
      final body = await RequestUtils.parseJsonBody(request);

      // 验证必需字段
      if (!RequestUtils.validateRequiredFields(body, ['content'])) {
        return ResponseUtils.validationError('日记内容不能为空');
      }

      // 创建日记模型
      final diary = DiaryModel.create(
        content: body['content'] as String,
        tags: body['tags'] as String?,
        mood: body['mood'] as String?,
        images: body['images'] as String?,
      );

      // 保存日记
      final diaryRepository = DiaryRepository.i;
      final diaryId = diaryRepository.save(diary);

      // 获取新创建的日记
      final newDiary = diaryRepository.getById(diaryId);
      if (newDiary == null) {
        return ResponseUtils.serverError('日记创建失败');
      }

      // 刷新日记列表
      _refreshDiaryList();

      // 转换为JSON格式
      final diaryJson = _diaryToJson(newDiary);

      return ResponseUtils.success(diaryJson, status: 201);
    } catch (e) {
      logger.e('创建日记失败: $e');
      return ResponseUtils.serverError('处理创建日记请求时发生错误');
    }
  }

  /// 更新日记
  Future<Response> _updateDiary(Request request, String id) async {
    try {
      final diaryId = int.tryParse(id);
      if (diaryId == null) {
        return ResponseUtils.validationError('无效的日记ID');
      }

      // 获取现有日记
      final diaryRepository = DiaryRepository.i;
      final existingDiary = diaryRepository.getById(diaryId);
      if (existingDiary == null) {
        return ResponseUtils.error('日记不存在', status: 404);
      }

      // 解析请求体
      final body = await RequestUtils.parseJsonBody(request);

      // 更新日记属性
      if (body.containsKey('content')) {
        existingDiary.content = body['content'] as String;
      }
      if (body.containsKey('tags')) {
        existingDiary.tags = body['tags'] as String?;
      }
      if (body.containsKey('mood')) {
        existingDiary.mood = body['mood'] as String?;
      }
      if (body.containsKey('images')) {
        existingDiary.entity.images = body['images'] as String?;
      }

      // 更新时间
      existingDiary.updatedAt = DateTime.now();

      // 保存更新
      diaryRepository.save(existingDiary);

      // 刷新日记列表
      _refreshDiaryList();

      // 转换为JSON格式
      final diaryJson = _diaryToJson(existingDiary);

      return ResponseUtils.success(diaryJson);
    } catch (e) {
      logger.e('更新日记失败: $e');
      return ResponseUtils.serverError('处理更新日记请求时发生错误');
    }
  }

  /// 删除日记
  Future<Response> _deleteDiary(Request request, String id) async {
    try {
      final diaryId = int.tryParse(id);
      if (diaryId == null) {
        return ResponseUtils.validationError('无效的日记ID');
      }

      // 删除日记
      final diaryRepository = DiaryRepository.i;
      final success = diaryRepository.delete(diaryId);
      if (!success) {
        return ResponseUtils.error('日记不存在或删除失败', status: 404);
      }

      // 刷新日记列表
      _refreshDiaryList();

      return ResponseUtils.success({'success': true});
    } catch (e) {
      logger.e('删除日记失败: $e');
      return ResponseUtils.serverError('处理删除日记请求时发生错误');
    }
  }

  /// 将日记模型转换为JSON格式
  Map<String, dynamic> _diaryToJson(DiaryModel diary) {
    return {
      'id': diary.id,
      'content': diary.content,
      'tags': diary.tags,
      'mood': diary.mood,
      'images': diary.imagesList.map((e) => FileService.i.convertLocalPathToWebPath(e)).toList(),
      'createdAt': diary.createdAt.toIso8601String(),
      'updatedAt': diary.updatedAt.toIso8601String(),
    };
  }

  void _refreshDiaryList() {
    var controller = getx.Get.find<app_controller.DiaryController>();
    controller.loadDiaries();
  }
}
