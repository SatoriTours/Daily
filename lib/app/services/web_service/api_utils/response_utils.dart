import 'dart:convert';
import 'package:shelf/shelf.dart';

/// API响应工具类
class ResponseUtils {
  /// 成功响应
  static Response success(dynamic data, {int status = 200}) {
    final Map<String, dynamic> body = {'code': 0, 'msg': '成功', 'data': data};
    return Response(
      status,
      body: jsonEncode(body),
      headers: {'Content-Type': 'application/json; charset=utf-8'},
    );
  }

  /// 错误响应
  static Response error(String message, {int status = 400, int? code}) {
    final Map<String, dynamic> body = {
      'code': code ?? status,
      'msg': message,
      'data': null,
    };
    return Response(
      status,
      body: jsonEncode(body),
      headers: {'Content-Type': 'application/json; charset=utf-8'},
    );
  }

  /// 未授权响应
  static Response unauthorized([String message = '未登录或会话已过期']) {
    return error(message, status: 401, code: 401);
  }

  /// 验证失败响应
  static Response validationError(String message) {
    return error(message, status: 422, code: 400);
  }

  /// 内部服务器错误响应
  static Response serverError([String message = '服务器内部错误']) {
    return error(message, status: 500, code: 500);
  }
}
