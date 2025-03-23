import 'dart:convert';
import 'package:shelf/shelf.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 请求处理工具类
class RequestUtils {
  /// 解析请求体为JSON
  static Future<Map<String, dynamic>> parseJsonBody(Request request) async {
    try {
      final String body = await request.readAsString();
      if (body.isEmpty) {
        return {};
      }
      return jsonDecode(body);
    } catch (e) {
      logger.e('解析请求体失败: $e');
      throw FormatException('无效的JSON请求体');
    }
  }

  /// 解析查询参数
  static Map<String, String> parseQueryParams(Request request) {
    return request.url.queryParameters;
  }

  /// 从请求cookie中获取会话ID
  static String? getSessionId(Request request) {
    final cookies = request.headers['cookie'];

    final cookieMap = parseCookies(cookies ?? '');

    // 获取session_id
    return cookieMap['session_id'];
  }

  /// 解析Cookie字符串为Map
  static Map<String, String> parseCookies(String cookieHeader) {
    final cookies = <String, String>{};
    final cookieParts = cookieHeader.split(';');

    for (var part in cookieParts) {
      final trimmedPart = part.trim();
      final index = trimmedPart.indexOf('=');
      if (index > 0) {
        final key = trimmedPart.substring(0, index);
        final value = trimmedPart.substring(index + 1);
        cookies[key] = value;
      }
    }

    return cookies;
  }

  /// 验证请求体中必需的字段
  static bool validateRequiredFields(Map<String, dynamic> body, List<String> requiredFields) {
    return requiredFields.every((field) => body.containsKey(field) && body[field] != null);
  }

  /// 过滤请求体中的字段，只保留允许的字段
  static Map<String, dynamic> filterBodyFields(Map<String, dynamic> body, List<String> allowedFields) {
    final filteredBody = <String, dynamic>{};
    for (final field in allowedFields) {
      if (body.containsKey(field)) {
        filteredBody[field] = body[field];
      }
    }
    return filteredBody;
  }
}
