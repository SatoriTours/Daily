import 'dart:async';
import 'dart:convert';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:dio/dio.dart';

import 'package:get/get.dart' as getx;

class AppWebSocketTunnel {
  WebSocketChannel? _channel;
  final String _httpForwardUrl = 'http://127.0.0.1:8888';
  final Dio _dio = Dio();
  String get _webSocketUrl => SettingService.i.getSetting(SettingService.webSocketUrlKey);
  String get _deviceId => SettingService.i.getSetting(SettingService.deviceIdKey);

  // 重连机制相关参数
  int _retryCount = 0;
  int _fibonacciDelay = 1;
  int _fibonacciPrevious = 0;
  final int _maxDelay = 100;
  bool _isConnecting = false;

  /// 连接状态
  getx.RxBool isConnected = false.obs;

  /// 连接 WebSocket
  Future<void> startConnect() async {
    if (_isConnecting) {
      logger.i('正在连接中，请稍后...');
      return;
    }

    _isConnecting = true;
    final webSocketUrl = _webSocketUrl;
    final deviceId = _deviceId;
    try {
      logger.i('尝试连接 WebSocket, 重试次数: $_retryCount, 地址: $webSocketUrl');
      _channel = WebSocketChannel.connect(Uri.parse(webSocketUrl));

      _channel!.stream.listen(
        _handleMessage,
        onError: _handleError,
        onDone: _handleDone,
      );
      await _channel!.ready;
      await _sendDeviceId(); // 连接成功后发送设备ID
      logger.i('WebSocket 已连接至 $webSocketUrl, 设备ID: $deviceId');
      _resetRetryState(); // 连接成功，重置重试状态
      isConnected.value = true; // 连接成功
    } catch (e) {
      logger.e('连接 WebSocket 失败: $e');
      _reconnect();
      isConnected.value = false; // 连接失败
    } finally {
      _isConnecting = false;
    }
  }

  String getWebAccessUrl() {
    final uri = Uri.parse(_webSocketUrl);
    final port = uri.port;
    final host = uri.host;
    return 'http://$host:$port/mobile/$_deviceId';
  }

  /// 处理接收到的消息
  void _handleMessage(dynamic message) {
    final messageMap = jsonDecode(message);
    final messageID = messageMap['message_id'] as String? ?? '';
    final data = messageMap['data'] as String? ?? '';
    _forwardMessage(messageID.toString(), data);
  }

  /// 处理错误
  void _handleError(dynamic error) {
    logger.e('WebSocket 发生错误: $error');
    isConnected.value = false;
    _reconnect();
  }

  /// 处理连接关闭
  void _handleDone() {
    logger.i('WebSocket 连接已关闭.');
    _isConnecting = false;
    isConnected.value = false;
    _reconnect();
  }

  /// 重置重连状态
  void _resetRetryState() {
    _retryCount = 0;
    _fibonacciDelay = 1;
    _fibonacciPrevious = 0;
  }

  /// 重连 WebSocket
  Future<void> _reconnect() async {
    if (_isConnecting) {
      logger.i('正在连接中，请稍后...');
      return;
    }

    _isConnecting = true;
    _retryCount++;

    // 计算斐波那契延迟
    int currentDelay = _fibonacciDelay;
    _fibonacciDelay = _fibonacciDelay + _fibonacciPrevious;
    _fibonacciPrevious = currentDelay;

    if (_fibonacciDelay > _maxDelay) {
      _resetRetryState();
    }

    logger.i('WebSocket 重连将在 $currentDelay 秒后进行, 当前重试次数: $_retryCount');
    await Future.delayed(Duration(seconds: currentDelay));
    _isConnecting = false;
    startConnect();
  }

  /// 发送设备ID
  Future<void> _sendDeviceId() async {
    try {
      _channel?.sink.add(_deviceId);
      logger.i('设备ID已发送: $_deviceId');
    } catch (e) {
      logger.e('发送设备ID失败: $e');
    }
  }

  /// 转发消息
  Future<void> _forwardMessage(String messageID, String data) async {
    try {
      final messageMap = jsonDecode(data);
      final requestPath = messageMap['path'] as String? ?? '';
      final headers = (messageMap['headers'] as Map?)?.cast<String, String>() ?? {};
      final body = messageMap['body'] as String? ?? '';
      final method = messageMap['method'] as String? ?? '';

      final forwardUrl = _httpForwardUrl + requestPath;
      logger.i('转发请求路径: $forwardUrl');

      final response = await _dio.request(
        forwardUrl,
        data: body,
        options: Options(method: method, headers: headers),
      );

      await _processResponse(response, forwardUrl, messageID);
    } catch (e) {
      logger.e('解析消息或转发失败: $e');
      _sendError('解析消息或转发失败');
    }
  }

  /// 处理转发后的响应
  Future<void> _processResponse(Response response, String forwardUrl, String messageID) async {
    if (response.statusCode == 200) {
      logger.i('消息已成功转发至 $forwardUrl');

      final responseData = {
        "http_code": response.statusCode,
        "content-type": response.headers['content-type']?.first ?? '',
        "body": response.toString(), // 服务器有可能返回的是json对象，所以这里使用toString，而不能使用response.data.toString()
      };

      final reponse = {
        "message_id": messageID,
        "data": jsonEncode(responseData),
      };

      final responseJson = jsonEncode(reponse);
      logger.i('转发消息成功: $responseJson');
      _channel?.sink.add(responseJson);
    } else {
      logger.e('转发消息失败. 状态码: ${response.statusCode}, body: ${response.data}');
      _sendError('转发消息失败. 状态码: ${response.statusCode}');
    }
  }

  /// 发送错误信息
  void _sendError(String errorMessage) {
    try {
      _channel?.sink.add('Error: $errorMessage');
      logger.e('错误信息已发送: $errorMessage');
    } catch (e) {
      logger.e('发送错误信息失败: $e');
    }
  }

  /// 断开连接
  void disconnect() {
    _channel?.sink.close();
    logger.i('WebSocket 已断开.');
    _resetRetryState();
    isConnected.value = false;
  }
}
