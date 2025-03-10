import 'dart:convert';
import 'dart:io';

import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shelf/shelf.dart' as shelf;
import 'package:shelf/shelf_io.dart' as shelf_io;
import 'package:shelf_router/shelf_router.dart';
import 'package:shelf_static/shelf_static.dart';
import 'package:path/path.dart' as path;

class AppHttpServer {
  final _router = Router();

  Future<void> start() async {
    await _createWebsiteDir();
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
      logger.e('[服务  WebService] Web服务启动失败: $e');
    }
  }

  void _registerRouter() {
    // 定义 /ping 路由，返回字符串 "pong"
    _router.get('/ping', (shelf.Request request) => shelf.Response.ok('pong'));
    _router.get('/', _homePage);
    _router.post('/api/v1/articles', _handleRequest(_createArticle));
  }

  Future<shelf.Response> _homePage(shelf.Request request) async {
    final websiteDir = await _getWebsiteDir();
    final indexFile = File(path.join(websiteDir, 'index.html'));
    if (indexFile.existsSync()) {
      return shelf.Response.found('/app');
    } else {
      final content = await rootBundle.loadString('assets/website/index.html');
      return shelf.Response.ok(content, headers: {'Content-Type': 'text/html; charset=utf-8'});
    }
  }

  Future<void> _registerStaticRouter() async {
    final websiteDir = await _getWebsiteDir();
    final staticHandler = createStaticHandler(websiteDir, defaultDocument: 'index.html', serveFilesOutsidePath: true);
    _router.mount('/app', staticHandler);
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

  Future<String> _getWebsiteDir() async {
    final appDocDir = await getApplicationDocumentsDirectory();
    return path.join(appDocDir.path, 'website');
  }

  Future<void> _createWebsiteDir() async {
    final websiteDir = Directory(await _getWebsiteDir());
    if (!websiteDir.existsSync()) {
      websiteDir.createSync(recursive: true);
      logger.i('创建静态资源目录: $websiteDir');
    }
  }

  // 验证密码中间件
  shelf.Middleware _authMiddleware() {
    return (shelf.Handler innerHandler) {
      return (shelf.Request request) async {
        final password = request.requestedUri.queryParameters['pwd'] ?? '';
        final expectedPassword = SettingRepository.getSetting(SettingService.webServerPasswordKey);

        if (password != expectedPassword) {
          return _response(401, "密码错误");
        }

        return await innerHandler(request);
      };
    };
  }
}
