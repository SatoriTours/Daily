import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:dio/dio.dart';

import 'package:daily_satori/app/utils/app_info_utils.dart';

/// WebSocket隧道类
///
/// 负责与WebSocket服务器建立连接，并转发HTTP请求
class AppWebSocketTunnel {
  WebSocketChannel? _channel;
  final Dio _dio = Dio();

  /// 本地HTTP服务地址
  final String _httpForwardUrl = 'http://127.0.0.1:8888';

  /// WebSocket服务器URL
  String get _webSocketUrl => SettingRepository.i.getSetting(SettingService.webSocketUrlKey);

  /// 设备ID
  String get _deviceId => SettingRepository.i.getSetting(SettingService.deviceIdKey);

  /// 重连机制相关参数
  int _retryCount = 0;
  int _fibonacciDelay = 1;
  int _fibonacciPrevious = 0;
  final int _maxDelay = 100;
  bool _isConnecting = false;

  /// 连接状态 - 使用 ValueNotifier 替代 GetX .obs
  final ValueNotifier<bool> isConnected = ValueNotifier(false);

  /// 开始连接WebSocket服务器
  Future<void> startConnect() async {
    if (_isConnecting) {
      logger.i('正在连接中，请稍后...');
      return;
    }

    _isConnecting = true;
    try {
      logger.i('尝试连接WebSocket: $_webSocketUrl (重试次数: $_retryCount)');

      _channel = WebSocketChannel.connect(Uri.parse(_webSocketUrl));

      // 设置监听器
      _channel!.stream.listen(_handleMessage, onError: _handleError, onDone: _handleDone);

      // 等待连接准备就绪
      await _channel!.ready;

      // 发送设备标识
      await _sendDeviceId();

      // 更新状态
      _resetRetryState();
      isConnected.value = true;

      logger.i('WebSocket连接成功: $_webSocketUrl, 设备ID: $_deviceId');
    } catch (e) {
      logger.e('连接WebSocket失败: $e');
      isConnected.value = false;
      _reconnect();
    } finally {
      _isConnecting = false;
    }
  }

  /// 获取Web访问URL
  String getWebAccessUrl() {
    final uri = Uri.parse(_webSocketUrl);
    return 'http://${uri.host}:${uri.port}/mobile/$_deviceId';
  }

  /// 处理接收到的消息
  void _handleMessage(dynamic message) {
    try {
      final messageMap = jsonDecode(message as String);
      final messageID = messageMap['message_id'] as String? ?? '';
      final data = messageMap['data'] as String? ?? '';

      logger.i('收到WebSocket消息, ID: $messageID');
      _forwardMessage(messageID, data);
    } catch (e) {
      logger.e('解析WebSocket消息失败: $e');
      _sendError('消息格式错误: $e');
    }
  }

  /// 处理WebSocket错误
  void _handleError(dynamic error) {
    logger.e('WebSocket错误: $error');
    isConnected.value = false;
    _reconnect();
  }

  /// 处理WebSocket连接关闭
  void _handleDone() {
    logger.i('WebSocket连接已关闭');
    isConnected.value = false;
    _isConnecting = false;
    _reconnect();
  }

  /// 重置重连状态
  void _resetRetryState() {
    _retryCount = 0;
    _fibonacciDelay = 1;
    _fibonacciPrevious = 0;
  }

  /// 重连WebSocket
  Future<void> _reconnect() async {
    // 非生产环境不进行重连
    if (!AppInfoUtils.isProduction) {
      logger.i('非生产环境，不进行重连');
      return;
    }

    if (_isConnecting) {
      logger.i('正在连接中，跳过重连');
      return;
    }

    _isConnecting = true;
    _retryCount++;

    // 计算斐波那契延迟
    final currentDelay = _fibonacciDelay;
    _fibonacciDelay = _fibonacciDelay + _fibonacciPrevious;
    _fibonacciPrevious = currentDelay;

    // 重置延迟如果超过最大值
    if (_fibonacciDelay > _maxDelay) {
      _resetRetryState();
    }

    logger.i('WebSocket将在$currentDelay秒后重连 (重试次数: $_retryCount)');
    await Future.delayed(Duration(seconds: currentDelay));

    _isConnecting = false;
    startConnect();
  }

  /// 发送设备ID到服务器
  Future<void> _sendDeviceId() async {
    try {
      _channel?.sink.add(_deviceId);
      logger.i('设备ID已发送: $_deviceId');
    } catch (e) {
      logger.e('发送设备ID失败: $e');
      rethrow;
    }
  }

  /// 转发消息到本地HTTP服务
  Future<void> _forwardMessage(String messageID, String data) async {
    try {
      // 解析消息内容
      final messageMap = jsonDecode(data);
      final requestPath = messageMap['path'] as String? ?? '';
      final headers = (messageMap['headers'] as Map?)?.cast<String, String>() ?? {};
      final body = messageMap['body'] as String? ?? '';
      final method = messageMap['method'] as String? ?? 'GET';

      // 构建转发URL
      final forwardUrl = '$_httpForwardUrl$requestPath';
      logger.i('转发HTTP请求: $method $forwardUrl');

      // 发送HTTP请求
      final response = await _dio.request(
        forwardUrl,
        data: body.isNotEmpty ? body : null,
        options: Options(method: method, headers: headers, validateStatus: (_) => true),
      );

      // 处理响应
      await _sendResponse(response, messageID);
    } catch (e) {
      logger.e('转发消息失败: $e');
      _sendError('转发请求失败: $e', messageID: messageID);
    }
  }

  /// 发送响应回WebSocket服务器
  Future<void> _sendResponse(Response response, String messageID) async {
    try {
      // 准备响应数据
      final responseData = {
        "http_code": response.statusCode,
        "content-type": response.headers.map['content-type']?.first ?? '',
        "body": response.data is String ? response.data : jsonEncode(response.data),
      };

      // 封装消息
      final responseMessage = {"message_id": messageID, "data": jsonEncode(responseData)};

      // 发送响应
      final responseJson = jsonEncode(responseMessage);
      _channel?.sink.add(responseJson);

      logger.i('HTTP响应已发送, ID: $messageID, 状态码: ${response.statusCode}');
    } catch (e) {
      logger.e('发送HTTP响应失败: $e');
      _sendError('发送响应失败: $e', messageID: messageID);
    }
  }

  /// 发送错误信息
  void _sendError(String errorMessage, {String? messageID}) {
    try {
      if (messageID != null) {
        // 结构化错误响应
        final standardErrorBody = {"code": 500, "msg": errorMessage, "data": null};

        final errorData = {"http_code": 500, "content-type": "application/json", "body": jsonEncode(standardErrorBody)};

        final errorResponse = {"message_id": messageID, "data": jsonEncode(errorData)};

        _channel?.sink.add(jsonEncode(errorResponse));
      } else {
        // 简单错误消息
        _channel?.sink.add(jsonEncode({"error": errorMessage}));
      }

      logger.e('错误信息已发送: $errorMessage');
    } catch (e) {
      logger.e('发送错误信息失败: $e');
    }
  }

  /// 断开WebSocket连接
  Future<void> disconnect() async {
    try {
      await _channel?.sink.close();
      logger.i('WebSocket连接已主动断开');
      _resetRetryState();
      isConnected.value = false;
    } catch (e) {
      logger.e('断开WebSocket连接失败: $e');
    }
  }
}
