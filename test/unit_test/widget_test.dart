import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
import '../test_config.dart' as test_config;

void main() {
  group('Daily Satori 路由配置测试', () {
    test('路由配置正确', () {
      expect(Routes.home, '/home');
      expect(Routes.articles, '/articles');
      expect(Routes.diary, '/diary');
      expect(Routes.books, '/books');
      expect(Routes.settings, '/settings');
      expect(Routes.aiChat, '/ai-chat');
      expect(Routes.articleDetail, '/article-detail');
      expect(Routes.weeklySummary, '/weekly-summary');
    });

    test('路由数量正确', () {
      final routes = [
        Routes.home,
        Routes.articles,
        Routes.diary,
        Routes.books,
        Routes.settings,
        Routes.aiChat,
        Routes.articleDetail,
        Routes.weeklySummary,
      ];
      expect(routes.length, 8);
    });
  });

  group('Daily Satori 基础Widget测试', () {
    testWidgets('Material widgets 应该正常渲染', (WidgetTester tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('Daily Satori Test')),
            body: const Center(child: Text('Hello, World!')),
          ),
        ),
      );

      expect(find.text('Daily Satori Test'), findsOneWidget);
      expect(find.text('Hello, World!'), findsOneWidget);
      expect(find.byType(AppBar), findsOneWidget);
      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('TextField 应该正常工作和渲染', (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Padding(
              padding: EdgeInsets.all(16.0),
              child: TextField(
                decoration: InputDecoration(
                  labelText: 'Test Input',
                  hintText: 'Enter text here',
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.byType(TextField), findsOneWidget);
      expect(find.text('Test Input'), findsOneWidget);
      expect(find.text('Enter text here'), findsOneWidget);

      await tester.tap(find.byType(TextField));
      await tester.enterText(find.byType(TextField), 'Test content');
      await tester.pump();

      expect(find.text('Test content'), findsOneWidget);
    });

    testWidgets('Button 交互应该正常工作', (WidgetTester tester) async {
      bool buttonPressed = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () {
                  buttonPressed = true;
                },
                child: const Text('Press me'),
              ),
            ),
          ),
        ),
      );

      expect(find.byType(ElevatedButton), findsOneWidget);
      expect(find.text('Press me'), findsOneWidget);
      expect(buttonPressed, isFalse);

      await tester.tap(find.byType(ElevatedButton));
      await tester.pump();

      expect(buttonPressed, isTrue);
    });
  });

  group('TestConfig 测试', () {
    test('AI URL 应该从环境变量读取或使用默认值', () {
      final url = test_config.TestConfig.aiUrl;
      expect(url, isNotEmpty);
      expect(url, contains('deepseek.com'));
    });

    test('AI Model 应该返回正确的默认值', () {
      expect(test_config.TestConfig.aiModel, 'deepseek-chat');
    });

    test('AI Token 应该从环境变量读取', () {
      final token = test_config.TestConfig.aiToken;
      expect(token, isNotEmpty);
    });

    test('hasAiConfig 在有配置时应该返回true', () {
      expect(test_config.TestConfig.hasAiConfig, isTrue);
    });

    test('printConfig 应该能正常执行', () {
      expect(() => test_config.TestConfig.printConfig(), returnsNormally);
    });
  });
}
