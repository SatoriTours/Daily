import 'dart:convert';
import 'package:shelf/shelf.dart';
import 'package:shelf_router/shelf_router.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/auth_controller.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/article_controller.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/diary_controller.dart';
import 'package:daily_satori/app/services/web_service/api_utils/response_utils.dart';
import 'package:daily_satori/app/services/web_service/api_utils/auth_middleware.dart';
import 'package:path/path.dart' as path;

/// API控制器主类，集成所有API路由
class ApiController {
  /// 创建API路由集合
  Router get router {
    final router = Router();

    // 身份认证API
    router.mount('/auth', AuthController().router);

    // 文章API
    router.mount('/articles', ArticleController().router);

    // 日记API
    router.mount('/diary', DiaryController().router);

    // 文件上传API
    final filePipeline = const Pipeline().addMiddleware(AuthMiddleware.requireAuth());
    router.post('/upload', filePipeline.addHandler(_handleFileUpload));

    // 添加404错误处理
    router.all('/<ignored|.*>', _notFoundHandler);

    return router;
  }

  /// 处理文件上传
  Future<Response> _handleFileUpload(Request request) async {
    try {
      // 验证请求内容类型
      final contentType = request.headers['content-type'] ?? '';
      if (!contentType.contains('multipart/form-data')) {
        return ResponseUtils.validationError('请求必须是multipart/form-data格式');
      }

      // 从请求中读取二进制数据
      final bytes = await request.read().expand((chunk) => chunk).toList();

      // 解析multipart表单数据（简化版）
      final uploadedFiles = await _parseMultipartFormData(bytes, contentType);
      if (uploadedFiles.isEmpty) {
        return ResponseUtils.validationError('未找到上传的文件');
      }

      // 处理上传的文件
      final results = <Map<String, dynamic>>[];
      for (var file in uploadedFiles) {
        // 保存文件到public目录
        final fileName = file['filename'] as String;
        final fileData = file['data'] as List<int>;

        // 确定文件在public目录中的路径
        final fileExtension = path.extension(fileName).toLowerCase();
        String subDir = 'other';

        // 根据文件类型决定存储的子目录
        if (['.jpg', '.jpeg', '.png', '.gif', '.svg', '.webp'].contains(fileExtension)) {
          subDir = 'img';
        } else if (['.css'].contains(fileExtension)) {
          subDir = 'css';
        } else if (['.js'].contains(fileExtension)) {
          subDir = 'js';
        } else if (['.ttf', '.woff', '.woff2', '.eot', '.otf'].contains(fileExtension)) {
          subDir = 'fonts';
        }

        // 保存文件
        final timestamp = DateTime.now().millisecondsSinceEpoch.toString();
        final saveFileName = '${timestamp}_$fileName';
        final relativePath = path.join(subDir, saveFileName);
        await FileService.i.saveToPublicDirectory(fileData, relativePath);

        // 添加结果
        results.add({
          'original_name': fileName,
          'saved_path': relativePath,
          'url': '/public/$relativePath',
          'size': fileData.length,
        });
      }

      return ResponseUtils.success(results);
    } catch (e) {
      logger.e('处理文件上传请求失败: $e');
      return ResponseUtils.serverError('处理文件上传失败');
    }
  }

  /// 解析multipart/form-data (简化版)
  Future<List<Map<String, dynamic>>> _parseMultipartFormData(List<int> bytes, String contentType) async {
    final result = <Map<String, dynamic>>[];

    try {
      // 从Content-Type获取boundary
      final boundaryMatch = RegExp(r'boundary=(.*)').firstMatch(contentType);
      if (boundaryMatch == null) {
        return result;
      }

      final boundary = '--${boundaryMatch.group(1)}';
      final data = utf8.decode(bytes);
      final parts = data.split(boundary);

      // 跳过第一个部分（通常为空）并忽略最后一个部分（结束标记）
      for (int i = 1; i < parts.length - 1; i++) {
        final part = parts[i];

        // 查找头部与正文的分隔点（两个换行符）
        final headerBodySplit = part.indexOf('\r\n\r\n');
        if (headerBodySplit == -1) continue;

        final headers = part.substring(0, headerBodySplit);
        final body = part.substring(headerBodySplit + 4);

        // 解析Content-Disposition头以获取文件名
        final filenameMatch = RegExp(r'filename="([^"]*)"').firstMatch(headers);
        if (filenameMatch == null) continue;

        final filename = filenameMatch.group(1)!;

        // 去除尾部的\r\n--
        final binaryData = body.substring(0, body.lastIndexOf('\r\n'));

        // 将字符串转换回二进制数据
        final fileData = utf8.encode(binaryData);

        result.add({'filename': filename, 'data': fileData});
      }
    } catch (e) {
      logger.e('解析multipart/form-data失败: $e');
    }

    return result;
  }

  /// 处理未找到的路由
  Response _notFoundHandler(Request request) {
    logger.w('API请求未找到: ${request.method} ${request.url}');
    return ResponseUtils.error('API路径不存在', status: 404);
  }

  /// 创建带错误处理的管道
  Handler createHandler() {
    return const Pipeline().addMiddleware(_errorHandler()).addHandler(router);
  }

  /// 错误处理中间件
  Middleware _errorHandler() {
    return (Handler innerHandler) {
      return (Request request) async {
        try {
          return await innerHandler(request);
        } catch (e, stackTrace) {
          logger.e('API请求处理错误: $e\n$stackTrace');
          return ResponseUtils.serverError('服务器内部错误');
        }
      };
    };
  }
}
