import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/auth_controller.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/article_controller.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/diary_controller.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';

/// API控制器主类，集成所有API路由
class ApiController {
  /// 创建API路由集合
  Router get router {
    final router = Router();

    // 身份认证API
    router.mount('/auth', AuthController().router);

    // 文章API
    router.mount('/articles', ArticleController().router);

    // 日记API
    router.mount('/diary', DiaryController().router);

    // 添加404错误处理
    router.all('/<ignored|.*>', _notFoundHandler);

    return router;
  }

  /// 处理未找到的路由
  Response _notFoundHandler(Request request) {
    logger.w('API请求未找到: ${request.method} ${request.url}');
    return ResponseUtils.error('API路径不存在', status: 404);
  }

  /// 创建带错误处理的管道
  Handler createHandler() {
    return const Pipeline().addMiddleware(_errorHandler()).addHandler(router);
  }

  /// 错误处理中间件
  Middleware _errorHandler() {
    return (Handler innerHandler) {
      return (Request request) async {
        try {
          return await innerHandler(request);
        } catch (e, stackTrace) {
          logger.e('API请求处理错误: $e\n$stackTrace');
          return ResponseUtils.serverError('服务器内部错误');
        }
      };
    };
  }
}
