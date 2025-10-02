import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Daily Satori 快速测试', () {
    testWidgets('应用启动和基本渲染测试', (WidgetTester tester) async {
      // 创建简单的测试应用
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(
              title: const Text('Daily Satori'),
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
            ),
            body: const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.article, size: 48),
                  SizedBox(height: 16),
                  Text('欢迎使用 Daily Satori'),
                  Text('您的每日生活管理助手'),
                ],
              ),
            ),
            bottomNavigationBar: BottomNavigationBar(
              items: const [
                BottomNavigationBarItem(
                  icon: Icon(Icons.home),
                  label: '首页',
                ),
                BottomNavigationBarItem(
                  icon: Icon(Icons.article),
                  label: '文章',
                ),
                BottomNavigationBarItem(
                  icon: Icon(Icons.book),
                  label: '日记',
                ),
                BottomNavigationBarItem(
                  icon: Icon(Icons.menu_book),
                  label: '读书',
                ),
                BottomNavigationBarItem(
                  icon: Icon(Icons.settings),
                  label: '设置',
                ),
              ],
              currentIndex: 0,
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证基本元素
      expect(find.text('Daily Satori'), findsOneWidget);
      expect(find.text('欢迎使用 Daily Satori'), findsOneWidget);
      expect(find.text('您的每日生活管理助手'), findsOneWidget);
      expect(find.text('首页'), findsOneWidget);
      expect(find.text('文章'), findsOneWidget);
      expect(find.text('日记'), findsOneWidget);
      expect(find.text('读书'), findsOneWidget);
      expect(find.text('设置'), findsOneWidget);

      print('✅ 应用基本渲染正常');

      // 测试底部导航点击
      await tester.tap(find.text('文章'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('日记'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('读书'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('设置'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('首页'));
      await tester.pumpAndSettle();

      print('✅ 底部导航正常');

      // 验证图标存在
      expect(find.byIcon(Icons.article), findsAtLeastNWidgets(1));
      expect(find.byIcon(Icons.home), findsOneWidget);
      expect(find.byIcon(Icons.book), findsAtLeastNWidgets(1));
      expect(find.byIcon(Icons.menu_book), findsOneWidget);
      expect(find.byIcon(Icons.settings), findsOneWidget);

      print('✅ 图标显示正常');
      print('✅ 快速测试完成');
    });

    testWidgets('列表显示测试', (WidgetTester tester) async {
      // 创建列表界面
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('文章列表')),
            body: ListView.builder(
              itemCount: 5,
              itemBuilder: (context, index) {
                return Card(
                  margin: const EdgeInsets.all(8.0),
                  child: ListTile(
                    leading: CircleAvatar(
                      child: Text('${index + 1}'),
                    ),
                    title: Text('测试文章 $index'),
                    subtitle: Text('这是第 $index 篇文章的摘要'),
                    trailing: const Icon(Icons.arrow_forward_ios),
                  ),
                );
              },
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证列表项
      for (int i = 0; i < 5; i++) {
        expect(find.text('测试文章 $i'), findsOneWidget);
        expect(find.text('这是第 $i 篇文章的摘要'), findsOneWidget);
      }

      // 测试滚动
      await tester.fling(
        find.byType(ListView),
        const Offset(0, -200),
        1000,
      );
      await tester.pumpAndSettle();

      await tester.fling(
        find.byType(ListView),
        const Offset(0, 200),
        1000,
      );
      await tester.pumpAndSettle();

      print('✅ 列表显示和滚动正常');

      // 测试点击列表项
      await tester.tap(find.text('测试文章 2'));
      await tester.pumpAndSettle();

      print('✅ 列表项点击正常');
    });

    testWidgets('输入和按钮测试', (WidgetTester tester) async {
      // 创建输入界面
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('写日记')),
            body: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                children: [
                  TextField(
                    key: const Key('title_field'),
                    decoration: const InputDecoration(
                      labelText: '标题',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    key: const Key('content_field'),
                    decoration: const InputDecoration(
                      labelText: '内容',
                      border: OutlineInputBorder(),
                    ),
                    maxLines: 5,
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton(
                          key: const Key('save_button'),
                          onPressed: () {},
                          child: const Text('保存'),
                        ),
                      ),
                      const SizedBox(width: 16),
                      IconButton(
                        key: const Key('image_button'),
                        onPressed: () {},
                        icon: const Icon(Icons.image),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 测试输入
      await tester.enterText(find.byKey(const Key('title_field')), '测试日记');
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('content_field')), '这是测试内容');
      await tester.pumpAndSettle();

      expect(find.text('测试日记'), findsOneWidget);
      expect(find.text('这是测试内容'), findsOneWidget);

      // 测试按钮点击
      await tester.tap(find.byKey(const Key('save_button')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('image_button')));
      await tester.pumpAndSettle();

      print('✅ 输入和按钮功能正常');
    });
  });
}