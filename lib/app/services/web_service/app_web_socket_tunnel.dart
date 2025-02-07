import 'dart:convert';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:dio/dio.dart';

class AppWebSocketTunnel {
  WebSocketChannel? _channel;
  final String _webSocketUrl = 'ws://10.0.2.2:3000/ws';
  final String _httpForwardUrl = 'http://127.0.0.1:8888';
  final Dio _dio = Dio();
  String get _deviceId => SettingService.i.getSetting(SettingService.deviceIdKey);

  Future<void> connect() async {
    try {
      _channel = WebSocketChannel.connect(Uri.parse(_webSocketUrl));

      _channel!.stream.listen((message) {
        _forwardMessage(message.toString());
      }, onError: (error) {
        logger.e('WebSocket 发生错误: $error');
        // Handle error, e.g., try to reconnect
      }, onDone: () {
        logger.i('WebSocket 连接已关闭.');
        // Handle connection closed, e.g., try to reconnect
      });

      // Send device ID upon connection
      _sendDeviceId();

      logger.i('WebSocket 已连接至 $_webSocketUrl, 设备ID: $_deviceId');
    } catch (e) {
      logger.e('连接 WebSocket 失败: $e');
    }
  }

  Future<void> _sendDeviceId() async {
    try {
      _channel?.sink.add(_deviceId);
      logger.i('设备ID已发送: $_deviceId');
    } catch (e) {
      logger.e('发送设备ID失败: $e');
    }
  }

  Future<void> _forwardMessage(String message) async {
    try {
      final Map<String, dynamic> messageMap = jsonDecode(message);
      final String requestPath = messageMap['path'] as String? ?? '';
      final Map<String, String> headers = (messageMap['headers'] as Map?)?.cast<String, String>() ?? {};
      final String body = messageMap['body'] as String? ?? '';
      final String method = messageMap['method'] as String? ?? '';

      String forwardUrl = _httpForwardUrl + requestPath;
      logger.i('转发请求路径: $forwardUrl');

      final response = await _dio.request(
        forwardUrl,
        data: body,
        options: Options(method: method, headers: headers),
      );

      if (response.statusCode == 200) {
        logger.i('消息已成功转发至 $forwardUrl');
        final responseData = {
          "http_code": response.statusCode,
          "Content-Type": response.headers['content-type']?.first ?? '',
          "body": response.toString(),
        };

        final responseJson = jsonEncode(responseData);
        _channel?.sink.add(responseJson);
      } else {
        logger.e('转发消息失败. 状态码: ${response.statusCode}, body: ${response.data}');
        _sendError('转发消息失败. 状态码: ${response.statusCode}');
      }
    } catch (e) {
      logger.e('解析消息或转发失败: $e');
      _sendError('解析消息或转发失败');
    }
  }

  void _sendError(String errorMessage) {
    try {
      _channel?.sink.add('Error: $errorMessage');
      logger.e('错误信息已发送: $errorMessage');
    } catch (e) {
      logger.e('发送错误信息失败: $e');
    }
  }

  void disconnect() {
    _channel?.sink.close();
    logger.i('WebSocket 已断开.');
  }
}
