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
late final Logger logger;

class LoggerService implements AppService {
  LoggerService._privateConstructor();
  static final LoggerService _instance = LoggerService._privateConstructor();
  static LoggerService get i => _instance;

  @override
  String get serviceName => 'LoggerService';

  @override
  ServicePriority get priority => ServicePriority.critical;

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
  @override
  List<String> log(LogEvent event) {
    final levelTag = _levelToTag(event.level);
    final message = event.message.toString();
    final caller = _callerTag(); // 形如 [PluginService+93]

    final lines = <String>['[$levelTag] $message     <= $caller'];

    if (event.error != null) {
      lines.add('↳ error: ${event.error}');
    }
    if (event.stackTrace != null) {
      lines.add('↳ stack: ${_shortStack(event.stackTrace!)}');
    }

    return lines;
  }

  String _levelToTag(Level level) {
    switch (level) {
      case Level.trace:
      case Level.verbose:
        return 'V';
      case Level.all:
        return 'V';
      case Level.debug:
        return 'D';
      case Level.info:
        return 'I';
      case Level.warning:
        return 'W';
      case Level.error:
        return 'E';
      case Level.fatal:
        return 'F';
      case Level.wtf:
        return 'F';
      case Level.off:
        return '-';
      case Level.nothing:
        return '-';
    }
  }

  /// 解析调用栈，提取 类名 与 行号。
  /// 返回形如 "[PluginService+93]" 的前缀。
  String _callerTag() {
    final st = StackTrace.current.toString();
    final lines = st.split('\n');

    // 过滤出第一个来自本项目且不是 logger 自身的帧
    // 常见帧格式：
    // #4      PluginService._loadConfigContent (package:daily_satori/app/services/plugin_service.dart:93:11)
    final framePattern = RegExp(r'#\d+\s+([^\s]+)\s+\((?:package:|file:).+?:(\d+):\d+\)');

    for (final line in lines) {
      if (line.contains('logger_service.dart')) continue; // 跳过本文件
      if (line.contains('package:logger/')) continue; // 跳过 logger 内部
      if (!line.contains('daily_satori')) continue; // 限定为本项目（基于包名）

      final m = framePattern.firstMatch(line);
      if (m != null) {
        final qualified = m.group(1) ?? '';
        final lineNo = m.group(2) ?? '0';

        // qualified 可能是 ClassName.method 或者 顶层函数名
        String className;
        if (qualified.contains('.')) {
          className = qualified.split('.').first;
        } else {
          // 顶层函数，退化为函数名
          className = qualified;
        }

        // 清理匿名闭包显示
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
}
