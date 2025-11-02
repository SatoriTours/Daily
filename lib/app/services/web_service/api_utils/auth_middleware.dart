import 'package:daily_satori/app/services/web_service/api_utils/request_utils.dart';
import 'package:shelf/shelf.dart';
import 'package:daily_satori/app/repositories/repositories.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/session_manager.dart';

/// 认证中间件
class AuthMiddleware {
  /// 验证请求是否已认证
  static Middleware requireAuth() {
    return (Handler innerHandler) {
      return (Request request) async {
        // 获取session ID
        final sessionId = RequestUtils.getSessionId(request);
        if (sessionId == null) {
          return ResponseUtils.unauthorized('未登录或会话已过期');
        }

        // 验证会话
        final session = await SessionManager.getSession(sessionId);
        if (session == null || !session.isAuthenticated) {
          return ResponseUtils.unauthorized('未登录或会话已过期');
        }

        // 将会话信息添加到请求中
        final updatedRequest = request.change(context: {'session_id': sessionId, 'isAuthenticated': true});

        return await innerHandler(updatedRequest);
      };
    };
  }

  /// 验证密码
  static bool verifyPassword(String password) {
    final expectedPassword = SettingRepository.i.getSetting(SettingService.webServerPasswordKey);
    return password == expectedPassword;
  }
}
