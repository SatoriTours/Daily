import 'dart:developer';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/services/service_base.dart';

const _logName = 'Satori';
late final Logger logger;

/// 长文本调试日志（仅开发环境输出）
void loggerVerbose(String message) {
  if (AppInfoUtils.isProduction) return;
  log('[VERBOSE] $message', name: _logName);
}

/// 日志服务，初始化并提供全局 logger 实例
class LoggerService implements AppService {
  LoggerService._privateConstructor();
  static final LoggerService _instance = LoggerService._privateConstructor();
  static LoggerService get i => _instance;

  @override
  String get serviceName => 'LoggerService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  @override
  Future<void> init() async {
    // 控制台输出配置：生产环境关闭 WebView 调试日志，生产环境日志级别设为 info
    PlatformInAppWebViewController.debugLoggingSettings.enabled = !AppInfoUtils.isProduction;
    Logger.level = AppInfoUtils.isProduction ? Level.info : Level.debug;
    logger = Logger(printer: SatoriPrinter(), output: _MyConsoleOutput());
  }

  @override
  void dispose() {}
}

/// 控制台输出实现：生产环境静默
class _MyConsoleOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    if (AppInfoUtils.isProduction) return;
    log(event.lines.join('\n'), name: _logName);
  }
}

/// 自定义日志打印器，输出格式：[I] [ClassName+Line] message <= caller
class SatoriPrinter extends LogPrinter {
  static const _callerColumn = 96;

  @override
  List<String> log(LogEvent event) {
    final levelTag = _levelToTag(event.level);
    final message = _truncateMessage(event.message.toString(), 50);
    final caller = _callerTag(event.stackTrace);

    final lines = <String>[_formatMainLine(levelTag, message, caller)];

    if (event.error != null) lines.add('↳ error: ${event.error}');
    if (event.stackTrace != null) lines.add('↳ stack: ${_shortStack(event.stackTrace!)}');

    return lines;
  }

  String _truncateMessage(String message, int maxLength) {
    final runes = message.runes.toList();
    return runes.length <= maxLength ? message : '${String.fromCharCodes(runes.take(maxLength))}...';
  }

  String _formatMainLine(String levelTag, String message, String caller) {
    final prefix = '[$levelTag] ';
    final paddingCount = (_callerColumn - prefix.runes.length - message.runes.length).clamp(1, 9999);
    return '$prefix$message${' ' * paddingCount}<= $caller';
  }

  String _levelToTag(Level level) {
    if (level == Level.trace || level == Level.all) return 'V';
    if (level == Level.debug) return 'D';
    if (level == Level.info) return 'I';
    if (level == Level.warning) return 'W';
    if (level == Level.error) return 'E';
    if (level == Level.fatal) return 'F';
    if (level == Level.off) return '-';
    return '?';
  }

  /// 解析调用栈，提取调用位置 [ClassName+Line]
  String _callerTag([StackTrace? captured]) {
    final st = (captured ?? StackTrace.current).toString();
    final lines = st.split('\n');
    const projectPath = 'package:daily_satori/';

    // 优先从本项目代码帧提取文件名 + 行号
    for (final line in lines) {
      if (_isSkipLine(line)) continue;
      if (line.contains(projectPath)) {
        final m = RegExp(r'\((?:package:|file:)([^:]+):(\d+):\d+\)').firstMatch(line);
        if (m != null) {
          final fileName = m.group(1)!.split('/').last;
          final base = fileName.endsWith('.dart') ? fileName.substring(0, fileName.length - 5) : fileName;
          return '[${_toPascalCase(base)}+${m.group(2)}]';
        }
      }
    }

    // 退回到函数限定名解析
    for (final line in lines) {
      if (_isSkipLine(line)) continue;
      final m = RegExp(r'#\d+\s+([^\s]+)\s+\((?:package:|file:).+?:(\d+):\d+\)').firstMatch(line);
      if (m != null) {
        var className = m.group(1)!;
        if (className.contains('.')) className = className.split('.').first;
        className = className.replaceAll('<anonymous closure>', 'closure');
        return '[$className+${m.group(2)}]';
      }
    }

    return '[Unknown+0]';
  }

  bool _isSkipLine(String line) {
    return line.contains('logger_service.dart') ||
        line.contains('package:logger/') ||
        line.contains('package:get/') ||
        line.contains('package:flutter/');
  }

  String _shortStack(StackTrace st, [int maxLines = 3]) {
    return st.toString().trim().split('\n').take(maxLines).join('\n');
  }

  String _toPascalCase(String s) {
    if (s.isEmpty) return s;
    final parts = s.split(RegExp(r'[_-]+'));
    final buf = StringBuffer();
    for (final p in parts) {
      if (p.isEmpty) continue;
      buf.write(p[0].toUpperCase());
      if (p.length > 1) buf.write(p.substring(1));
    }
    return buf.toString();
  }
}
