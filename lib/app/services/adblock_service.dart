import 'dart:io';

import 'package:flutter/services.dart';

import 'package:dio/dio.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class ADBlockService {
  ADBlockService._privateConstructor();
  static final ADBlockService _instance = ADBlockService._privateConstructor();
  static ADBlockService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] ADBlockService");
    await _parseRulesContent();
    logger.i('处理adblock规则完成');
    _downloadRules();
  }

  // final String _easylistUrl = 'https://easylist.to/easylist/easylist.txt';
  final String _easylistUrl =
      'https://easylist-downloads.adblockplus.org/v3/full/easylistchina+easylist.txt';
  final String _localEasylistFile = 'assets/easylistchina+easylist.txt';
  final List<String> _elementHidingRules = [];
  final Map<String, List<String>> _elementHidingRulesBySite = {};

  List<String> get elementHidingRules => _elementHidingRules;
  Map<String, List<String>> get elementHidingRulesBySite =>
      _elementHidingRulesBySite;

  Future<void> _downloadRules() async {
    if (!isProduction) {
      return;
    }
    final easylistPath = await _getEasylistPath();
    try {
      final response = await Dio().download(_easylistUrl, easylistPath);
      if (response.statusCode == 200) {
        logger.i('下载adblock规则文件到: $easylistPath');
      } else {
        logger.i('下载adblock规则失败: ${response.statusCode}');
      }
    } catch (e) {
      logger.i('下载adblock规则失败: $e');
    }
  }

  Future<void> _parseRulesContent() async {
    String content = await _getRulesContent();
    List<String> lines = content.split('\n');
    String rulesType = '';
    for (String line in lines) {
      if (line.startsWith('!-----') && rulesType.isNotEmpty) {
        rulesType = '';
        continue;
      }
      if (line.startsWith('!-----') && rulesType.isEmpty) {
        if (line.contains("General element hiding rules")) {
          rulesType = 'elementHidingRules';
        } else if (line.contains("Specific element hiding rules")) {
          rulesType = 'elementHidingRulesBySite';
        }
        continue;
      }
      if (rulesType == 'elementHidingRules') {
        if (line.startsWith("##")) {
          _elementHidingRules.add(line.replaceFirst("##", '').trim());
        }
        continue;
      }
      if (rulesType == 'elementHidingRulesBySite') {
        final info = line.split("##");
        if (info.length == 2) {
          final site = info[0].trim(), rule = info[1].trim();
          if (_elementHidingRulesBySite.containsKey(site)) {
            _elementHidingRulesBySite[site]!.add(rule);
          } else {
            _elementHidingRulesBySite[site] = [rule];
          }
        }
        continue;
      }
      // logger.i('处理规则: $line');
    }
  }

  Future<String> _getEasylistPath() async {
    return path.join(await _getCacheDirectory(), 'easylist.txt');
  }

  Future<String> _getCacheDirectory() async {
    final directory = await getTemporaryDirectory();
    return directory.path;
  }

  Future<String> _getRulesContent() async {
    final easylistPath = await _getEasylistPath();
    final file = File(easylistPath);

    String rulesContent;
    if (await file.exists()) {
      rulesContent = await file.readAsString();
      logger.i('成功读取规则文件: $easylistPath');
    } else {
      rulesContent = await rootBundle.loadString(_localEasylistFile);
      logger.i('规则文件不存在, 使用本地文件: $_localEasylistFile');
    }
    return rulesContent;
  }
}
