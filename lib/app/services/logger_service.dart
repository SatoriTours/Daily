import 'dart:developer';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/services/service_base.dart';
// 终端宽度对齐逻辑已移除：保持输出简洁，无需额外对齐处理

/// 日志名称前缀，便于在 IDE/调试控制台中过滤
const String _logName = 'Satori';

/// 全局日志实例。
/// 注意：使用前须先完成应用服务初始化（`LoggerService.i.init()`）。
/// 示例：
///   logger.i('初始化完成');
///   logger.e('处理失败', error: e, stackTrace: st);
///   loggerVerbose('长文本内容...'); // 仅开发环境输出，用于调试
late final Logger logger;

/// 长文本调试日志（仅在开发环境输出）
///
/// 用于打印完整的长文本内容，方便代码调试。
/// 在生产环境中此函数不会输出任何内容。
///
/// 示例：
///   loggerVerbose('完整的 HTML 内容: $htmlContent');
///   loggerVerbose('API 响应: $jsonResponse');
void loggerVerbose(String message) {
  if (AppInfoUtils.isProduction) return;
  log('[VERBOSE] $message', name: _logName);
}

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
    // 仅通过 name 字段携带品牌标识，避免消息体重复 [Satori]
    log('[初始化服务] LoggerService', name: _logName);

    // 打开/关闭 WebView 内部调试日志
    PlatformInAppWebViewController.debugLoggingSettings.enabled = !AppInfoUtils.isProduction;

    // 生产环境仅输出较重要的信息（info 及以上），开发环境尽量完整
    Logger.level = AppInfoUtils.isProduction ? Level.info : Level.debug;

    // 自定义打印器与输出：
    // 目标格式（IDE 控制台查看）：
    //   [I] [PluginService+93] 消息内容
    //   ↳ error: <错误对象>
    //   ↳ stack: <堆栈前几行>
    logger = Logger(printer: SatoriPrinter(), output: _MyConsoleOutput());
  }

  @override
  void dispose() {}
}

class _MyConsoleOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    // 生产环境保持安静（避免在 Release 控制台输出过多信息）；
    // 如需上报后端或写入本地文件，可在此处接入。
    if (AppInfoUtils.isProduction) return;

    final logString = event.lines.join('\n');
    log(logString, name: _logName);
  }
}

/// 自定义日志打印器。
///
/// 输出格式（非生产环境，会通过 dart:developer.log 打印到控制台）：
///   [I] [ClassName+Line] 消息内容
///   ↳ error: <错误对象>            // 当存在 error 时出现
///   ↳ stack: <堆栈前几行>         // 当存在 stackTrace 时出现
class SatoriPrinter extends LogPrinter {
  static const int _callerColumn = 96;

  @override
  List<String> log(LogEvent event) {
    final levelTag = _levelToTag(event.level);
    final rawMessage = event.message.toString();
    // 截断消息到 36 个字符（按 rune 计算）
    final message = _truncateMessage(rawMessage, maxLength: 50);
    // 优先使用事件自带的堆栈（若调用端传入 `stackTrace: StackTrace.current`，定位更准确）
    final caller = _callerTag(event.stackTrace); // 形如 [PluginService+93]

    final lines = <String>[_formatMainLine(levelTag: levelTag, message: message, caller: caller)];

    if (event.error != null) {
      lines.add('↳ error: ${event.error}');
    }
    if (event.stackTrace != null) {
      lines.add('↳ stack: ${_shortStack(event.stackTrace!)}');
    }

    return lines;
  }

  /// 截断消息到指定长度
  String _truncateMessage(String message, {required int maxLength}) {
    final runes = message.runes.toList();
    if (runes.length <= maxLength) return message;
    return '${String.fromCharCodes(runes.take(maxLength))}...';
  }

  String _formatMainLine({required String levelTag, required String message, required String caller}) {
    final prefix = '[$levelTag] ';

    // 目标：让每一行的 "<=" 从同一列开始，便于肉眼扫读。
    // 说明：IDE/终端字体非等宽时对齐不保证 100% 精准，但在常见等宽控制台中可读性会明显提升。
    final prefixWidth = _displayWidth(prefix);
    final messageWidth = _displayWidth(message);
    final paddingCount = (_callerColumn - (prefixWidth + messageWidth)).clamp(1, 9999);

    return '$prefix$message${' ' * paddingCount}<= $caller';
  }

  int _displayWidth(String s) {
    // 简化处理：按 rune 数量计算显示宽度。
    // 对 CJK 全角字符的真实宽度（2列）不做特殊处理，避免引入复杂依赖。
    return s.runes.length;
  }

  String _levelToTag(Level level) {
    // 避免使用已弃用的枚举，统一映射到当前级别集合
    if (level == Level.trace || level == Level.all) return 'V';
    if (level == Level.debug) return 'D';
    if (level == Level.info) return 'I';
    if (level == Level.warning) return 'W';
    if (level == Level.error) return 'E';
    // 兼容较新的严重级别
    if (level == Level.fatal) return 'F';
    if (level == Level.off) return '-';
    // 其它或未来值
    return '?';
  }

  /// 解析调用栈，提取 类名 与 行号。
  /// 返回形如 "[PluginService+93]" 的前缀。
  ///
  /// 优先解析调用端传入的 [captured] 堆栈，没有则回退为当前堆栈。
  String _callerTag([StackTrace? captured]) {
    final st = (captured ?? StackTrace.current).toString();
    final lines = st.split('\n');

    // 优先：从本项目 package 帧中提取 文件名 + 行号
    // 典型帧：
    // #4      Some.fn (package:daily_satori/app/pages/diary/views/diary_view.dart:83:11)
    const projectPath = 'package:daily_satori/';
    final fileLocPattern = RegExp(r'\((?:package:|file:)([^:]+):(\d+):\d+\)');

    for (final line in lines) {
      if (line.contains('logger_service.dart')) continue; // 跳过本文件
      if (line.contains('package:logger/')) continue; // 跳过 logger 内部
      if (line.contains('package:get/')) continue; // 跳过 GetX/Obx 等
      if (line.contains('package:flutter/')) continue; // 跳过 Flutter 框架

      if (line.contains(projectPath)) {
        final m = fileLocPattern.firstMatch(line);
        if (m != null) {
          final filePath = m.group(1) ?? '';
          final lineNo = m.group(2) ?? '0';

          // 提取文件名并转为 PascalCase 作为“类名”展示
          final fileName = filePath.split('/').last; // e.g. diary_view.dart
          final base = fileName.endsWith('.dart') ? fileName.substring(0, fileName.length - 5) : fileName;
          final classLike = _toPascalCase(base);
          return '[$classLike+$lineNo]';
        }
      }
    }

    // 次选：退回到函数限定名（ClassName.method）解析
    final fnPattern = RegExp(r'#\d+\s+([^\s]+)\s+\((?:package:|file:).+?:(\d+):\d+\)');
    for (final line in lines) {
      if (line.contains('logger_service.dart')) continue;
      if (line.contains('package:logger/')) continue;
      if (line.contains('package:get/')) continue;
      if (line.contains('package:flutter/')) continue;

      final m = fnPattern.firstMatch(line);
      if (m != null) {
        final qualified = m.group(1) ?? '';
        final lineNo = m.group(2) ?? '0';
        String className = qualified.contains('.') ? qualified.split('.').first : qualified;
        className = className.replaceAll('<anonymous closure>', 'closure');
        return '[$className+$lineNo]';
      }
    }

    // 兜底
    return '[Unknown+0]';
  }

  // no padding required with simple arrow format

  /// 将堆栈压缩为前几行，便于在控制台快速定位
  String _shortStack(StackTrace st, {int maxLines = 3}) {
    final ls = st.toString().trim().split('\n');
    if (ls.isEmpty) return '';
    return ls.take(maxLines).join('\n');
  }

  String _toPascalCase(String s) {
    if (s.isEmpty) return s;
    // 支持下划线与中划线分隔
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
