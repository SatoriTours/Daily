import 'dart:convert';
import 'dart:io';

import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/web_service/api_controllers/api_controller.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:shelf/shelf.dart' as shelf;
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_router/shelf_router.dart';
import 'package:shelf_static/shelf_static.dart';

class AppHttpServer {
  final _router = Router();
  final _apiController = ApiController();

  Future<void> start() async {
    await _registerStaticRouter();
    _registerRouter();

    // 构建中间件及处理链，添加请求日志中间件
    final pipeline = const shelf.Pipeline().addMiddleware(shelf.logRequests());
    final handler = pipeline.addHandler(_router.call);

    // 启动服务器，监听所有IPv4地址的 8888 端口
    try {
      final server = await shelf_io.serve(handler, InternetAddress.anyIPv4, 8888);
      logger.i('[服务 WebService] Web服务启动成功: ${server.address.host}:${server.port}');
    } catch (e) {
      logger.e('[服务 WebService] Web服务启动失败: $e');
    }
  }

  void _registerRouter() {
    // 定义 /ping 路由，返回字符串 "pong"
    _router.get('/ping', (shelf.Request request) => shelf.Response.ok('pong'));

    // 注册旧的API
    _router.post('/api/v1/articles', _handleRequest(_createArticle));

    // 注册新的RESTful API
    _router.mount('/api/v2', _apiController.createHandler());
  }

  Future<void> _registerStaticRouter() async {
    // 图片文件目录
    final imagesDir = FileService.i.imagesBasePath;
    final imagesHandler = createStaticHandler(imagesDir, serveFilesOutsidePath: true);
    _router.mount('/images', imagesHandler);

    // 内置静态资源
    _router.mount('/assets', _assetHandler);

    logger.i('静态资源目录已注册: 图片(/images), 内置资源(/assets)');
  }

  /// 处理内置静态资源
  Future<shelf.Response> _assetHandler(shelf.Request request) async {
    try {
      final assetPath = 'assets${request.url.path}';
      final data = await rootBundle.load(assetPath);

      // 确定内容类型
      String contentType = 'application/octet-stream';
      if (assetPath.endsWith('.html')) {
        contentType = 'text/html; charset=utf-8';
      } else if (assetPath.endsWith('.css')) {
        contentType = 'text/css; charset=utf-8';
      } else if (assetPath.endsWith('.js')) {
        contentType = 'application/javascript; charset=utf-8';
      } else if (assetPath.endsWith('.json')) {
        contentType = 'application/json; charset=utf-8';
      } else if (assetPath.endsWith('.png')) {
        contentType = 'image/png';
      } else if (assetPath.endsWith('.jpg') || assetPath.endsWith('.jpeg')) {
        contentType = 'image/jpeg';
      } else if (assetPath.endsWith('.gif')) {
        contentType = 'image/gif';
      } else if (assetPath.endsWith('.svg')) {
        contentType = 'image/svg+xml';
      }

      return shelf.Response.ok(data.buffer.asUint8List(), headers: {'Content-Type': contentType});
    } catch (e) {
      logger.e('加载内置资源失败: $e');
      return shelf.Response.notFound('资源不存在');
    }
  }

  Future<shelf.Response> _createArticle(Map<String, String> params) async {
    if (Get.currentRoute == Routes.SHARE_DIALOG) {
      return _response(1, '请先把上个页面保存完成');
    }

    Get.toNamed(Routes.SHARE_DIALOG, arguments: {'articleID': 0, 'shareURL': params['url'], 'update': false});

    return _response(0, '请在 APP 中继续操作');
  }

  Function _handleRequest(Function handler) {
    return (shelf.Request request) async {
      final body = await request.readAsString();
      final params = Uri.splitQueryString(body);
      final password = params['password'];
      final expectedPassword = SettingRepository.getSetting(SettingService.webServerPasswordKey);

      if (password != expectedPassword) {
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

  shelf.Response _response(int code, String msg, {int status = 200}) {
    final body = jsonEncode({"code": code, "msg": msg});
    return shelf.Response(status, body: body, headers: {'Content-Type': 'application/json; charset=utf-8'});
  }
}
