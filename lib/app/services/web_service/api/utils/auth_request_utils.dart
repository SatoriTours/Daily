import 'package:daily_satori/app/services/web_service/api/session/session_manager.dart';
import 'package:daily_satori/app/services/web_service/api/utils/request_utils.dart';
import 'package:daily_satori/app/services/web_service/api/utils/response_utils.dart';
import 'package:shelf/shelf.dart';

/// 认证相关的请求工具
class AuthRequestUtils {
  /// 从请求中校验会话认证；失败时返回对应 Response。
  static Future<Response?> ensureAuthenticated(Request request) async {
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
}
