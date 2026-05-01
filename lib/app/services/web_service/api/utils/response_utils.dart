import 'dart:convert';

import 'package:shelf/shelf.dart';

/// Web API 响应工具
///
/// 统一 API 的 JSON 响应结构：
/// `{ code, msg, data }`
class ResponseUtils {
  static const _headers = {'Content-Type': 'application/json; charset=utf-8'};

  /// 成功响应
  static Response success(dynamic data, {int status = 200}) {
    final body = {'code': 0, 'msg': '成功', 'data': data};
    return Response(status, body: jsonEncode(body), headers: _headers);
  }

  /// 错误响应
  static Response error(String message, {int status = 400, int? code}) {
    final body = {'code': code ?? status, 'msg': message, 'data': null};
    return Response(status, body: jsonEncode(body), headers: _headers);
  }

  /// 未授权响应
  static Response unauthorized([String message = '未登录或会话已过期']) {
    return error(message, status: 401, code: 401);
  }

  /// 参数验证失败
  static Response validationError(String message) {
    // 兼容历史：HTTP 422，但业务 code 仍使用 400
    return error(message, status: 422, code: 400);
  }

  /// 服务器错误
  static Response serverError([String message = '服务器内部错误']) {
    return error(message, status: 500, code: 500);
  }
}
