import 'dart:io';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:daily_satori/app/services/web_service/app_http_server.dart';
import 'package:daily_satori/app/services/web_service/app_web_socket_tunnel.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// Web服务管理类，负责HTTP服务器和WebSocket隧道的初始化和管理
class WebService {
  WebService._();

  static final WebService _instance = WebService._();
  static WebService get i => _instance;

  final _httpServer = AppHttpServer();
  final _webSocketTunnel = AppWebSocketTunnel();

  /// 获取WebSocket隧道实例
  AppWebSocketTunnel get webSocketTunnel => _webSocketTunnel;

  /// Web服务端口号（已迁移至 WebServiceConfig）
  static int get httpPort => WebServiceConfig.httpPort;

  /// 初始化Web服务
  Future<void> init() async {
    await _httpServer.start();
    // await _webSocketTunnel.startConnect();
  }

  /// 获取应用访问地址
  ///
  /// 返回格式: http://IP地址:端口
  /// 如果是移动网络或无网络，则返回提示信息
  Future<String> getAppAddress() async {
    final connectivityResult = await Connectivity().checkConnectivity();

    if (connectivityResult.contains(ConnectivityResult.wifi)) {
      return 'http://${await _getLocalIpAddress()}:$httpPort';
    } else if (connectivityResult.contains(ConnectivityResult.mobile)) {
      return '移动网络暂不支持直接访问';
    } else {
      return '无网络连接，无法访问';
    }
  }

  /// 获取本机IP地址
  ///
  /// 优先获取WiFi或以太网接口的IPv4地址
  Future<String> _getLocalIpAddress() async {
    String ipAddress = 'localhost';
    try {
      final interfaces = await NetworkInterface.list();
      for (var interface in interfaces) {
        // 优先查找WiFi和以太网接口
        if (interface.name == 'en0' ||
            interface.name == 'eth0' ||
            interface.name == 'wlan0' ||
            interface.name.contains('Wi-Fi')) {
          for (var address in interface.addresses) {
            if (address.type == InternetAddressType.IPv4) {
              return address.address;
            }
          }
        }
      }

      // 如果没有找到首选接口，遍历查找任意IPv4地址
      for (var interface in interfaces) {
        for (var address in interface.addresses) {
          if (address.type == InternetAddressType.IPv4 && !address.address.startsWith('127.')) {
            return address.address;
          }
        }
      }
    } catch (e) {
      logger.e('获取IP地址失败: $e');
    }
    return ipAddress;
  }
}
