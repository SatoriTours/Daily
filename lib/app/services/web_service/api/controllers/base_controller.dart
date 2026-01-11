import 'dart:async';

import 'package:daily_satori/app/services/web_service/api/middleware/auth_middleware.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';

/// Controller 基类
///
/// 提供认证路由辅助方法，减少各 Controller 中的重复代码。
abstract class BaseController {
  /// 子类实现，返回路由配置
  Router get router;

  /// 需认证的 Pipeline
  static final Pipeline authedPipeline = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());

  /// 包装需认证的 handler
  static Handler authed(FutureOr<Response> Function(Request) handler) {
    return authedPipeline.addHandler(handler);
  }

  /// 包装带路径参数的认证 handler（1 个参数）
  static Future<Response> Function(Request, String) authedWithId(FutureOr<Response> Function(Request, String) handler) {
    return (request, id) async {
      final h = authedPipeline.addHandler((req) => handler(req, id));
      return await h(request);
    };
  }

  /// 包装带路径参数的认证 handler（2 个参数）
  static Future<Response> Function(Request, String, String) authedWithIds(
    FutureOr<Response> Function(Request, String, String) handler,
  ) {
    return (request, id1, id2) async {
      final h = authedPipeline.addHandler((req) => handler(req, id1, id2));
      return await h(request);
    };
  }
}
