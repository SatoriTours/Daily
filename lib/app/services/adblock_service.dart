import 'dart:io';

import 'package:flutter/services.dart';

import 'package:dio/dio.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class ADBlockService {
  // 单例模式
  ADBlockService._();
  static final ADBlockService _instance = ADBlockService._();
  static ADBlockService get i => _instance;

  // 常量定义
  static const String _easylistUrl = 'https://easylist-downloads.adblockplus.org/v3/full/easylistchina+easylist.txt';
  static const String _localEasylistFile = 'assets/easylistchina+easylist.txt';

  // 规则存储
  final List<String> _elementHidingRules = [];
  final Map<String, List<String>> _elementHidingRulesBySite = {};

  // Getters
  List<String> get elementHidingRules => _elementHidingRules;
  Map<String, List<String>> get elementHidingRulesBySite => _elementHidingRulesBySite;

  // 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] ADBlockService");
    await _initRules();
    logger.i('处理adblock规则完成');
  }

  // 初始化规则
  Future<void> _initRules() async {
    await _parseRulesContent();
    if (isProduction) {
      _downloadLatestRules();
    }
  }

  // 下载最新规则
  Future<void> _downloadLatestRules() async {
    final easylistPath = await _getEasylistPath();
    try {
      final response = await Dio().download(_easylistUrl, easylistPath);
      if (response.statusCode == 200) {
        logger.i('下载adblock规则文件到: $easylistPath');
      } else {
        logger.w('下载adblock规则失败: ${response.statusCode}');
      }
    } catch (e) {
      logger.e('下载adblock规则失败: $e');
    }
  }

  // 解析规则内容
  Future<void> _parseRulesContent() async {
    final content = await _getRulesContent();
    final lines = content.split('\n');

    var currentRuleType = '';

    for (final line in lines) {
      if (line.startsWith('!-----')) {
        currentRuleType = _determineRuleType(line);
        continue;
      }

      if (currentRuleType.isEmpty) continue;

      _processRule(currentRuleType, line);
    }
  }

  // 确定规则类型
  String _determineRuleType(String line) {
    if (line.contains("General element hiding rules")) {
      return 'elementHidingRules';
    } else if (line.contains("Specific element hiding rules")) {
      return 'elementHidingRulesBySite';
    }
    return '';
  }

  // 处理单条规则
  void _processRule(String ruleType, String line) {
    switch (ruleType) {
      case 'elementHidingRules':
        if (line.startsWith("##")) {
          _elementHidingRules.add(line.replaceFirst("##", '').trim());
        }
        break;
      case 'elementHidingRulesBySite':
        final parts = line.split("##");
        if (parts.length == 2) {
          final site = parts[0].trim();
          final rule = parts[1].trim();
          _elementHidingRulesBySite.putIfAbsent(site, () => []).add(rule);
        }
        break;
    }
  }

  // 获取规则文件路径
  Future<String> _getEasylistPath() async {
    final cacheDir = await getTemporaryDirectory();
    return path.join(cacheDir.path, 'easylist.txt');
  }

  // 获取规则内容
  Future<String> _getRulesContent() async {
    final easylistPath = await _getEasylistPath();
    final file = File(easylistPath);

    if (await file.exists()) {
      logger.i('成功读取规则文件: $easylistPath');
      return file.readAsString();
    }

    logger.i('规则文件不存在, 使用本地文件: $_localEasylistFile');
    return rootBundle.loadString(_localEasylistFile);
  }
}
