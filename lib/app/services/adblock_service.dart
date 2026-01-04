import 'dart:io';

import 'package:flutter/services.dart';

import 'package:dio/dio.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/config/app_config.dart';

class ADBlockService implements AppService {
  // 单例模式
  ADBlockService._();
  static final ADBlockService _instance = ADBlockService._();
  static ADBlockService get i => _instance;

  @override
  String get serviceName => 'ADBlockService';

  @override
  ServicePriority get priority => ServicePriority.high;

  // 常量已迁移至 UrlConfig
  static String get _easylistUrl => UrlConfig.easylistUrl;
  static String get _localEasylistFile => UrlConfig.localEasylistFile;

  // CSS规则存储
  final List<String> _cssRules = []; // 普通CSS选择器规则
  final Map<String, List<String>> _domainCssRules = {}; // 域名特定的CSS规则

  // 网络规则存储
  final List<String> _exactNetworkRules = []; // 精确匹配规则
  final List<String> _containsNetworkRules = []; // 包含匹配规则
  final List<RegExp> _regexNetworkRules = []; // 正则匹配规则

  // 例外规则存储
  final List<String> _exactExceptionRules = []; // 精确匹配例外规则
  final List<String> _containsExceptionRules = []; // 包含匹配例外规则
  final List<RegExp> _regexExceptionRules = []; // 正则匹配例外规则

  // Getters
  List<String> get cssRules => _cssRules;
  Map<String, List<String>> get domainCssRules => _domainCssRules;
  List<String> get exactNetworkRules => _exactNetworkRules;
  List<String> get containsNetworkRules => _containsNetworkRules;
  List<RegExp> get regexNetworkRules => _regexNetworkRules;
  List<String> get exactExceptionRules => _exactExceptionRules;
  List<String> get containsExceptionRules => _containsExceptionRules;
  List<RegExp> get regexExceptionRules => _regexExceptionRules;

  // 初始化服务
  @override
  Future<void> init() async {
    await _loadRules();
  }

  @override
  void dispose() {}

  // 加载规则
  Future<void> _loadRules() async {
    final content = await _getRulesContent();
    _parseRules(content);

    if (AppInfoUtils.isProduction) {
      _updateRules();
    }
  }

  // 解析规则
  void _parseRules(String content) {
    final lines = content.split('\n');
    for (var line in lines) {
      line = line.trim();
      if (line.isEmpty || line.startsWith('!')) continue;

      if (line.startsWith('@@')) {
        _parseExceptionRule(line);
      } else if (line.contains('##') || line.contains('#?#')) {
        _parseCssRule(line);
      } else {
        _parseNetworkRule(line);
      }
    }
  }

  // 解析规则的通用方法
  void _parseRule(String line, {bool isException = false}) {
    if (isException) {
      line = line.substring(2); // 移除 @@ 前缀
    }

    if (line.startsWith('||')) {
      line = line.substring(2);
    }

    final hasWildcard = line.contains('*');
    final isExactMatch = line.endsWith('^');

    if (hasWildcard) {
      // 将通配符转换为正则表达式
      var pattern = line.replaceAll('*', '.*').replaceAll('?', '\\?');
      if (isExactMatch) {
        pattern = pattern.substring(0, pattern.length - 1) + r'$';
      }

      // 暂时不考虑下面的情况
      //1.  @@||adnet.qq.com^$~third-party，
      //2. bing.com/translator/api/Translate/TranslateArray?$third-party,xmlhttprequest，
      //3. ||piclect.com^*.gif$third-party
      //4. ||ggwan.com^$third-party,domain=~linghit.com
      //5. /d/*-*-*.ap|$script,third-party
      pattern = pattern.replaceAll(RegExp(r'\|.*$'), ''); // 移除 | 后面的内容， 例如 /d/*-*-*.ap|$script,third-party
      pattern = pattern.replaceAll(RegExp(r'\$.*$'), ''); // 移除 $ 后面的内容， 例如 ||ggwan.com^$third-party,domain=~linghit.com
      try {
        final regExp = RegExp(pattern);
        isException ? _regexExceptionRules.add(regExp) : _regexNetworkRules.add(regExp);
      } catch (e) {
        // 目前还有如下格式需要去兼容
        // [Satori] [E]  解析正则表达式规则失败 /^https\?:\/\/..*bit(ly)\?\.(com => FormatException: Unterminated group /^https\?:\/\/..*bit(ly)\?\.(com
        // [Satori] [E]  解析正则表达式规则失败 /^https\?:\/\/..*\.(com => FormatException: Unterminated group /^https\?:\/\/..*\.(com
        // [Satori] [E]  解析正则表达式规则失败 /^https\?:\/\/..*\.(club => FormatException: Unterminated group /^https\?:\/\/..*\.(club
        // [Satori] [E]  解析正则表达式规则失败 /^https\?:\/\/..*\/..*(sw[0-9a-z._-]{1,6} => FormatException: Unterminated group /^https\?:\/\/..*\/..*(sw[0-9a-z._-]{1,6}
        // logger.e('解析正则表达式规则失败 $pattern => $e');
      }
    } else {
      if (isExactMatch) {
        final rule = line.substring(0, line.length - 1);
        isException ? _exactExceptionRules.add(rule) : _exactNetworkRules.add(rule);
      } else {
        isException ? _containsExceptionRules.add(line) : _containsNetworkRules.add(line);
      }
    }
  }

  // 解析例外规则
  void _parseExceptionRule(String line) {
    _parseRule(line, isException: true);
  }

  // 解析网络规则
  void _parseNetworkRule(String line) {
    _parseRule(line);
  }

  // 解析CSS规则
  void _parseCssRule(String line) {
    final parts = line.split(RegExp(r'##|#\?#'));
    if (parts.length == 2) {
      final domains = parts[0];
      final cssSelector = parts[1];

      if (domains.isEmpty) {
        _cssRules.add(cssSelector);
      } else {
        for (var domain in domains.split(',')) {
          domain = domain.replaceAll('~', '');
          _domainCssRules.putIfAbsent(domain, () => []).add(cssSelector);
        }
      }
    }
  }

  // 更新规则
  Future<void> _updateRules() async {
    try {
      final response = await Dio().get(_easylistUrl);
      if (response.statusCode == 200) {
        final file = File(await _getLocalPath());
        await file.writeAsString(response.data);
        logger.i('广告规则更新成功');
      }
    } catch (e) {
      logger.e('更新广告规则失败: $e');
    }
  }

  // 获取规则内容
  Future<String> _getRulesContent() async {
    final filePath = await _getLocalPath();
    final file = File(filePath);

    if (await file.exists()) {
      return file.readAsString();
    }
    return rootBundle.loadString(_localEasylistFile);
  }

  // 获取本地存储路径
  Future<String> _getLocalPath() async {
    final dir = await getTemporaryDirectory();
    return path.join(dir.path, 'easylist.txt');
  }
}
