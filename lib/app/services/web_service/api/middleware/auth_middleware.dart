import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/web_service/api/session/session_manager.dart';
import 'package:daily_satori/app/services/web_service/api/utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:shelf/shelf.dart';

/// Web API 认证中间件
class AuthMiddleware {
  /// 要求请求已登录
  static Middleware requireAuth() {
    return (Handler innerHandler) {
      return (Request request) async {
        final sessionId = RequestUtils.getSessionId(request);
        if (sessionId == null) {
          return ResponseUtils.unauthorized('未登录或会话已过期');
        }

        final session = await SessionManager.getSession(sessionId);
        if (session == null || !session.isAuthenticated) {
          return ResponseUtils.unauthorized('未登录或会话已过期');
        }

        final updatedRequest = request.change(context: {'session_id': sessionId, 'isAuthenticated': true});

        return innerHandler(updatedRequest);
      };
    };
  }

  /// 校验 Web 服务访问密码
  static bool verifyPassword(String password) {
    final expectedPassword = SettingRepository.i.getSetting(SettingService.webServerPasswordKey);
    return password == expectedPassword;
  }
}
