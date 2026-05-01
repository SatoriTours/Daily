// ignore_for_file: avoid_print

// 测试配置文件
//
// 用于测试环境下的AI配置和其他设置

import 'dart:io';

class TestConfig {
  /// AI配置URL
  static String get aiUrl {
    return Platform.environment['TEST_AI_URL'] ?? 'https://api.deepseek.com';
  }

  /// AI配置Token
  static String get aiToken {
    return Platform.environment['TEST_AI_TOKEN'] ?? '';
  }

  /// AI模型名称
  static String get aiModel {
    return Platform.environment['TEST_AI_MODEL'] ?? 'deepseek-chat';
  }

  /// 是否配置了AI
  static bool get hasAiConfig {
    return aiUrl.isNotEmpty && aiToken.isNotEmpty;
  }

  /// 打印配置信息（隐藏token）
  static void printConfig() {
    print('=== 测试配置 ===');
    print('AI URL: ${aiUrl.isNotEmpty ? aiUrl : '未配置'}');
    print('AI Token: ${aiToken.isNotEmpty ? '已配置' : '未配置'}');
    print('AI Model: $aiModel');
    print('===============');
  }
}
