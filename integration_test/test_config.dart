// ignore_for_file: avoid_print

import 'dart:io';

/// 集成测试配置文件
///
/// AI 配置优先级：
/// 1. 运行时环境变量 (Platform.environment)
/// 2. 编译时环境变量 (--dart-define)
/// 3. 默认值
class TestConfig {
  // AI 配置（优先从运行时环境变量读取，其次从编译时定义读取）
  static String get aiToken {
    // 优先使用运行时环境变量
    final runtimeToken = Platform.environment['TEST_AI_TOKEN'] ?? '';
    if (runtimeToken.isNotEmpty) return runtimeToken;
    // 其次使用编译时定义
    return const String.fromEnvironment('TEST_AI_TOKEN', defaultValue: '');
  }

  static String get aiUrl {
    final runtimeUrl = Platform.environment['TEST_AI_URL'] ?? '';
    if (runtimeUrl.isNotEmpty) return runtimeUrl;
    return const String.fromEnvironment('TEST_AI_URL', defaultValue: 'https://api.deepseek.com');
  }

  static String get aiModel {
    final runtimeModel = Platform.environment['TEST_AI_MODEL'] ?? '';
    if (runtimeModel.isNotEmpty) return runtimeModel;
    return const String.fromEnvironment('TEST_AI_MODEL', defaultValue: 'deepseek-chat');
  }

  /// 是否已配置AI
  static bool get hasAiConfig => aiToken.isNotEmpty && aiUrl.isNotEmpty;

  // 测试用的文章URL
  static const List<String> testArticleUrls = [
    'https://blog.tymscar.com/posts/gleamaoc2025/',
    'https://juejin.cn/post/6844903919807303694',
    'https://zh.wikipedia.org/wiki/Flutter',
  ];

  // 测试用的书籍搜索关键词
  static const List<String> testBookKeywords = ['三体', '论语', '红楼梦', '1984'];

  // 测试用的日记内容模板
  static const String diaryTemplate = '''# 测试日记标题

## 今天的收获
1. 学习了 Flutter 测试
2. 完成了集成测试

## 思考与感悟
通过这次测试，我理解了...

## 明日计划
- [ ] 完成项目
- [ ] 学习新知识
- [ ] 锻炼身体
''';

  // 测试用的读书感悟模板
  static const String viewpointTemplate = '''# 《书名》读后感悟

## 核心观点
这本书最打动我的是...

## 主要收获
1.
2.
3.

## 实践应用
我可以将这些理念应用到...
''';

  // 测试用的 AI 聊天问题
  static const List<String> testAIQuestions = ['帮我搜索关于测试的文章', '总结一下最近的日记', '推荐一些书籍', '今天我应该做什么'];

  // 测试等待时间（秒）
  static const int shortWait = 2;
  static const int mediumWait = 5;
  static const int longWait = 10;

  // 测试用的用户名和设置
  static const String testUserName = '测试用户';
  static const String testUserEmail = 'test@example.com';

  // 打印配置信息
  static void printConfig() {
    print('[TestConfig] AI Token: ${aiToken.isEmpty ? "未配置" : "已配置"}');
    print('[TestConfig] AI URL: ${aiUrl.isEmpty ? "未配置" : aiUrl}');
    print('[TestConfig] AI Model: $aiModel');
  }
}
