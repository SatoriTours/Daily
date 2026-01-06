import 'dart:developer';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/services/service_base.dart';

const _logName = 'Satori';
late final Logger logger;

/// 长文本调试日志（仅开发环境输出）
void loggerVerbose(String message) {
  if (!AppInfoUtils.isProduction) log('[VERBOSE] $message', name: _logName);
}

/// 日志服务
class LoggerService implements AppService {
  LoggerService._();
  static final LoggerService i = LoggerService._();

  @override
  String get serviceName => 'LoggerService';
  @override
  ServicePriority get priority => ServicePriority.critical;
  @override
  void dispose() {}

  @override
  Future<void> init() async {
    PlatformInAppWebViewController.debugLoggingSettings.enabled = !AppInfoUtils.isProduction;
    Logger.level = AppInfoUtils.isProduction ? Level.info : Level.debug;
    logger = Logger(printer: _SatoriPrinter(), output: _SatoriOutput());
  }
}

/// 控制台输出：生产环境静默
class _SatoriOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    if (!AppInfoUtils.isProduction) log(event.lines.join('\n'), name: _logName);
  }
}

/// 日志格式：[I] message <= [FileName+Line]
class _SatoriPrinter extends LogPrinter {
  // 格式配置常量
  static const _maxMessageLength = 120; // 消息最大长度（超出则截断）
  static const _targetColumn = 136; // 调用位置目标列数（用于对齐）

  static final _frameRe = RegExp(r'\((?:package:|file:)([^:]+):(\d+):\d+\)');
  static const _skipPatterns = ['logger_service.dart', 'package:logger/', 'package:get/', 'package:flutter/'];

  @override
  List<String> log(LogEvent event) {
    final tag = _levelTag(event.level);
    final msg = _truncate(event.message.toString(), _maxMessageLength);
    final caller = _extractCaller(event.stackTrace);
    final pad = (_targetColumn - tag.length - 3 - msg.runes.length).clamp(1, 999);

    return [
      '[$tag] $msg${' ' * pad}<= $caller',
      if (event.error != null) '↳ error: ${event.error}',
      if (event.stackTrace != null) '↳ stack: ${event.stackTrace.toString().split('\n').take(3).join('\n')}',
    ];
  }

  String _levelTag(Level level) => switch (level) {
    Level.trace || Level.all => 'V',
    Level.debug => 'D',
    Level.info => 'I',
    Level.warning => 'W',
    Level.error => 'E',
    Level.fatal => 'F',
    _ => '-',
  };

  String _truncate(String s, int max) {
    final runes = s.runes.toList();
    if (runes.length <= max) return s;
    return '${String.fromCharCodes(runes.take(max))}...';
  }

  String _extractCaller([StackTrace? st]) {
    for (final line in (st ?? StackTrace.current).toString().split('\n')) {
      if (_skipPatterns.any(line.contains)) continue;
      final match = _frameRe.firstMatch(line);
      if (match != null) {
        final file = match.group(1)!.split('/').last.replaceAll('.dart', '');
        return '[${_toPascal(file)}+${match.group(2)}]';
      }
    }
    return '[Unknown+0]';
  }

  String _toPascal(String s) =>
      s.split(RegExp(r'[_-]+')).where((p) => p.isNotEmpty).map((p) => p[0].toUpperCase() + p.substring(1)).join();
}
