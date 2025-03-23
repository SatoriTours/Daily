import 'dart:convert';
import 'package:shelf/shelf.dart';

/// API响应工具类
class ResponseUtils {
  /// 成功响应
  static Response success(dynamic data, {int status = 200}) {
    final Map<String, dynamic> body = {'success': true, 'data': data};
    return Response(status, body: jsonEncode(body), headers: {'Content-Type': 'application/json; charset=utf-8'});
  }

  /// 错误响应
  static Response error(String message, {int status = 400, int? code}) {
    final Map<String, dynamic> body = {
      'success': false,
      'error': {'message': message, if (code != null) 'code': code},
    };
    return Response(status, body: jsonEncode(body), headers: {'Content-Type': 'application/json; charset=utf-8'});
  }

  /// 未授权响应
  static Response unauthorized([String message = '未授权访问']) {
    return error(message, status: 401);
  }

  /// 验证失败响应
  static Response validationError(String message) {
    return error(message, status: 422);
  }

  /// 内部服务器错误响应
  static Response serverError([String message = '服务器内部错误']) {
    return error(message, status: 500);
  }
}
