import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import 'dart:convert';
import 'dart:io';

import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/api_controller.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:flutter/services.dart';
import 'package:shelf/shelf.dart' as shelf;
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_router/shelf_router.dart';
import 'package:shelf_static/shelf_static.dart';

/// HTTP服务器实现类
///
/// 提供REST API和静态资源服务
class AppHttpServer {
  final _router = Router();
  final _apiController = ApiController();
  HttpServer? _server;

  /// 启动HTTP服务器
  Future<void> start() async {
    // 注册静态资源路由
    await _registerStaticRoutes();

    // 注册API路由
    _registerApiRoutes();

    // 构建中间件及处理链
    final pipeline = const shelf.Pipeline().addMiddleware(shelf.logRequests()).addMiddleware(_corsMiddleware());

    final handler = pipeline.addHandler(_router.call);

    // 启动服务器
    try {
      _server = await shelf_io.serve(handler, InternetAddress.anyIPv4, WebService.httpPort);

      // logger.i('[服务 WebService] Web服务启动成功: ${_server!.address.host}:${_server!.port}');

      return;
    } catch (e) {
      logger.e('[服务 WebService] Web服务启动失败: $e');
      rethrow;
    }
  }

  /// 注册API路由
  void _registerApiRoutes() {
    // 健康检查路由
    _router.get('/ping', (shelf.Request request) => shelf.Response.ok('pong'));

    // 注册旧的API
    _router.post('/api/v1/articles', _handleRequest(_createArticle));

    // 注册新的RESTful API
    _router.mount('/api/v2', _apiController.createHandler());

    // logger.i('API路由已注册: /ping, /api/v1/articles, /api/v2/**');
  }

  /// 注册静态资源路由
  Future<void> _registerStaticRoutes() async {
    // 图片文件目录（转换为绝对路径）
    final imagesDir = FileService.i.toAbsolutePath(FileService.i.imagesBasePath);
    final imagesHandler = createStaticHandler(imagesDir, serveFilesOutsidePath: true);

    final diaryImagesDir = FileService.i.toAbsolutePath(FileService.i.diaryImagesBasePath);
    final diaryImagesHandler = createStaticHandler(diaryImagesDir, serveFilesOutsidePath: true);

    _router.mount('/images', imagesHandler);
    _router.mount('/diary_images', diaryImagesHandler);

    // 内置静态资源（仅限 website 目录）
    _router.mount('/website', _websiteHandler);

    // 管理后台页面
    _router.get('/', _adminHandler);
    _router.get('/admin', _adminHandler);
    _router.get('/admin/', _adminHandler);
  }

  /// 处理内置静态资源（仅限 website 目录）
  Future<shelf.Response> _websiteHandler(shelf.Request request) async {
    try {
      // 只允许访问 assets/website 目录下的资源
      final requestPath = request.url.path;

      // 安全检查：防止路径遍历攻击
      if (requestPath.contains('..')) {
        return shelf.Response.forbidden('非法路径');
      }

      final assetPath = 'assets/website/$requestPath';
      logger.i('加载内置资源: $assetPath');

      final data = await rootBundle.load(assetPath);

      // 确定内容类型
      final contentType = _getContentType(assetPath);

      return shelf.Response.ok(data.buffer.asUint8List(), headers: {'Content-Type': contentType});
    } catch (e) {
      logger.e('加载内置资源失败: $e');
      return shelf.Response.notFound('资源不存在');
    }
  }

  /// 管理后台页面处理
  Future<shelf.Response> _adminHandler(shelf.Request request) async {
    try {
      final data = await rootBundle.load('assets/website/admin.html');
      return shelf.Response.ok(data.buffer.asUint8List(), headers: {'Content-Type': 'text/html; charset=utf-8'});
    } catch (e) {
      logger.e('加载管理后台页面失败: $e');
      return shelf.Response.notFound('管理后台页面不存在');
    }
  }

  /// 根据文件扩展名获取内容类型
  String _getContentType(String filePath) {
    final extension = filePath.split('.').last.toLowerCase();

    switch (extension) {
      case 'html':
        return 'text/html; charset=utf-8';
      case 'css':
        return 'text/css; charset=utf-8';
      case 'js':
        return 'application/javascript; charset=utf-8';
      case 'json':
        return 'application/json; charset=utf-8';
      case 'png':
        return 'image/png';
      case 'jpg':
      case 'jpeg':
        return 'image/jpeg';
      case 'gif':
        return 'image/gif';
      case 'svg':
        return 'image/svg+xml';
      default:
        return 'application/octet-stream';
    }
  }

  /// 创建文章API处理方法
  Future<shelf.Response> _createArticle(Map<String, String> params) async {
    // 路由检查：如果当前不在根页面（例如打开了详情页或对话框），则阻止操作
    if (AppNavigation.navigatorKey.currentState?.canPop() ?? false) {
      return _response(1, '请先把上个页面保存完成');
    }

    AppNavigation.toNamed(Routes.shareDialog, arguments: {'articleID': 0, 'shareURL': params['url'], 'update': false});

    return _response(0, '请在 APP 中继续操作');
  }

  /// 请求处理包装器
  Function _handleRequest(Function handler) {
    return (shelf.Request request) async {
      final body = await request.readAsString();
      final params = Uri.splitQueryString(body);

      // 验证密码
      if (!_verifyPassword(params['password'])) {
        return _response(2, '密码错误');
      }

      try {
        return handler(params);
      } catch (e) {
        logger.e('处理请求失败: $e');
        return shelf.Response.internalServerError(body: '处理请求失败');
      }
    };
  }

  /// 验证密码
  bool _verifyPassword(String? password) {
    final expectedPassword = SettingRepository.i.getSetting(SettingService.webServerPasswordKey);
    return password == expectedPassword;
  }

  /// 生成标准响应
  shelf.Response _response(int code, String msg, {int status = 200, dynamic data}) {
    final body = jsonEncode({
      "code": code,
      "msg": msg,
      "data": data ?? (code == 0 ? {"success": true} : null),
    });
    return shelf.Response(status, body: body, headers: {'Content-Type': 'application/json; charset=utf-8'});
  }

  /// CORS中间件，允许跨域请求
  shelf.Middleware _corsMiddleware() {
    return (shelf.Handler innerHandler) {
      return (shelf.Request request) async {
        // 处理预检请求
        if (request.method == 'OPTIONS') {
          return shelf.Response.ok('', headers: _getCorsHeaders());
        }

        // 处理正常请求
        final response = await innerHandler(request);
        return response.change(headers: {..._getCorsHeaders(), ...response.headers});
      };
    };
  }

  /// 获取CORS头
  Map<String, String> _getCorsHeaders() => {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Origin, Content-Type, X-Auth-Token, Authorization',
    'Access-Control-Allow-Credentials': 'true',
    'Access-Control-Max-Age': '86400',
  };

  /// 关闭服务器
  Future<void> close() async {
    await _server?.close();
    logger.i('HTTP服务器已关闭');
  }
}
