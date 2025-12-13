import 'dart:convert';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:shelf/shelf.dart';

/// Web API 请求工具
class RequestUtils {
  /// 解析 JSON 请求体
  static Future<Map<String, dynamic>> parseJsonBody(Request request) async {
    try {
      final body = await request.readAsString();
      if (body.isEmpty) return {};
      return jsonDecode(body);
    } catch (e) {
      logger.e('[WebService] 解析请求体失败', error: e);
      throw const FormatException('无效的JSON请求体');
    }
  }

  /// 解析查询参数
  static Map<String, String> parseQueryParams(Request request) => request.url.queryParameters;

  /// 从 Cookie 中获取 session_id
  static String? getSessionId(Request request) {
    final cookieHeader = request.headers['cookie'];
    final cookieMap = parseCookies(cookieHeader ?? '');
    return cookieMap['session_id'];
  }

  /// 解析 Cookie header
  static Map<String, String> parseCookies(String cookieHeader) {
    final cookies = <String, String>{};
    for (final part in cookieHeader.split(';')) {
      final trimmed = part.trim();
      final index = trimmed.indexOf('=');
      if (index <= 0) continue;

      final key = trimmed.substring(0, index);
      final value = trimmed.substring(index + 1);
      cookies[key] = value;
    }
    return cookies;
  }

  /// 校验必填字段存在且不为 null
  static bool validateRequiredFields(Map<String, dynamic> body, List<String> requiredFields) {
    return requiredFields.every((field) => body.containsKey(field) && body[field] != null);
  }

  /// 过滤请求体字段
  static Map<String, dynamic> filterBodyFields(Map<String, dynamic> body, List<String> allowedFields) {
    final filtered = <String, dynamic>{};
    for (final field in allowedFields) {
      if (body.containsKey(field)) filtered[field] = body[field];
    }
    return filtered;
  }

  /// 获取 Content-Type
  static String? getContentType(Request request) => request.headers['content-type'];
}
