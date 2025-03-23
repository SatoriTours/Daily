import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_service/api_utils/auth_middleware.dart';
import 'package:daily_satori/app/services/web_service/api_utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/session_manager.dart';

/// 认证控制器
class AuthController {
  /// 创建路由
  Router get router {
    final router = Router();

    // 登录API
    router.post('/login', _login);

    // 退出登录API
    router.post('/logout', _logout);

    // 认证状态检查API
    router.get('/status', _status);

    return router;
  }

  /// 登录处理
  Future<Response> _login(Request request) async {
    try {
      // 解析请求体
      final body = await RequestUtils.parseJsonBody(request);

      // 验证必需字段
      if (!RequestUtils.validateRequiredFields(body, ['password'])) {
        return ResponseUtils.validationError('密码不能为空');
      }

      final password = body['password'] as String;

      // 验证密码
      if (!AuthMiddleware.verifyPassword(password)) {
        return ResponseUtils.error('密码错误', status: 401);
      }

      // 创建新会话
      final session = SessionManager.createSession();
      session.authenticate('satori'); // 使用默认用户名

      // 返回成功响应，带会话cookie
      final response = ResponseUtils.success({'success': true});
      return SessionManager.createSessionResponse(response, session);
    } catch (e) {
      logger.e('登录失败: $e');
      return ResponseUtils.serverError('处理登录请求时发生错误');
    }
  }

  /// 退出登录处理
  Future<Response> _logout(Request request) async {
    try {
      final sessionId = RequestUtils.getSessionId(request);
      if (sessionId != null) {
        SessionManager.destroySession(sessionId);
      }

      return ResponseUtils.success({'success': true});
    } catch (e) {
      logger.e('退出登录失败: $e');
      return ResponseUtils.serverError('处理退出登录请求时发生错误');
    }
  }

  /// 认证状态检查处理
  Future<Response> _status(Request request) async {
    try {
      final sessionId = RequestUtils.getSessionId(request);
      if (sessionId == null) {
        return ResponseUtils.success({'authenticated': false});
      }

      final session = await SessionManager.getSession(sessionId);
      if (session == null || !session.isAuthenticated) {
        return ResponseUtils.success({'authenticated': false});
      }

      return ResponseUtils.success({'authenticated': true});
    } catch (e) {
      logger.e('状态检查失败: $e');
      return ResponseUtils.serverError('处理状态检查请求时发生错误');
    }
  }
}
