import 'dart:io';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:daily_satori/app/services/web_service/app_http_server.dart';
import 'package:daily_satori/app/services/web_service/app_web_socket_tunnel.dart';

class WebService {
  WebService._();

  static final WebService _instance = WebService._();
  static WebService get i => _instance;

  final _httpServer = AppHttpServer();
  final _webSocketTunnel = AppWebSocketTunnel();
  Future<void> init() async {
    logger.i("[初始化服务] WebService");
    await _httpServer.start();
    await _webSocketTunnel.startConnect();
  }

  Future<String> getAppAddress() async {
    final connectivityResult = await (Connectivity().checkConnectivity());
    if (connectivityResult.contains(ConnectivityResult.wifi)) {
      String ipAddress = 'localhost';
      try {
        final interfaces = await NetworkInterface.list();
        for (var interface in interfaces) {
          if (interface.name == 'en0' || interface.name == 'eth0' || interface.name == 'wlan0') {
            for (var address in interface.addresses) {
              if (address.type == InternetAddressType.IPv4) {
                ipAddress = address.address;
                break;
              }
            }
            if (ipAddress != 'localhost') {
              break;
            }
          }
        }
      } catch (e) {
        logger.e('获取IP地址失败: $e');
      }
      return 'http://$ipAddress:8888';
    } else if (connectivityResult.contains(ConnectivityResult.mobile)) {
      return '5G网络暂不支持';
    } else {
      return '没有网络，无法访问';
    }
  }
}
