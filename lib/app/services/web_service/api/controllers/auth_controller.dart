import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api/middleware/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api/session/session_manager.dart';
import 'package:daily_satori/app/services/web_service/api/utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';

/// 认证控制器
class AuthController {
  Router get router {
    final router = Router();
    final authed = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());
    router.post('/login', _login);
    router.post('/logout', authed.addHandler(_logout));
    router.get('/status', authed.addHandler(_status));
    return router;
  }

  Future<Response> _login(Request request) async {
    try {
      final body = await RequestUtils.parseJsonBody(request);
      if (!RequestUtils.validateRequiredFields(body, ['password'])) {
        return ResponseUtils.validationError('密码不能为空');
      }

      final password = body['password'] as String;
      if (!AuthMiddleware.verifyPassword(password)) {
        return ResponseUtils.error('密码错误', status: 401);
      }

      final session = await SessionManager.createSession();
      await session.authenticate('satori');

      final response = ResponseUtils.success({'success': true});
      return SessionManager.createSessionResponse(response, session);
    } catch (e) {
      logger.e('[WebService][Auth] 登录失败', error: e);
      return ResponseUtils.serverError('处理登录请求时发生错误');
    }
  }

  Future<Response> _logout(Request request) async {
    try {
      final sessionId = RequestUtils.getSessionId(request);
      if (sessionId != null) SessionManager.destroySession(sessionId);
      return ResponseUtils.success({'success': true});
    } catch (e) {
      logger.e('[WebService][Auth] 退出登录失败', error: e);
      return ResponseUtils.serverError('处理退出登录请求时发生错误');
    }
  }

  Future<Response> _status(Request request) async {
    try {
      final sessionId = RequestUtils.getSessionId(request);
      if (sessionId == null) {
        return ResponseUtils.success({'authenticated': false});
      }

      final session = await SessionManager.getSession(sessionId);
      final authenticated = session != null && session.isAuthenticated;
      return ResponseUtils.success({'authenticated': authenticated});
    } catch (e) {
      logger.e('[WebService][Auth] 状态检查失败', error: e);
      return ResponseUtils.serverError('处理状态检查请求时发生错误');
    }
  }
}
