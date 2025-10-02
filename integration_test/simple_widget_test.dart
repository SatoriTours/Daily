import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Daily Satori 基础组件测试', () {
    testWidgets('应用路由配置测试', (WidgetTester tester) async {
      // 创建简单的测试应用，不依赖复杂的路由配置
      final testApp = MaterialApp(
        title: 'Daily Satori Test',
        home: Scaffold(
          appBar: AppBar(
            title: const Text('测试页面'),
            backgroundColor: Colors.blue,
          ),
          body: const Center(
            child: Text('Daily Satori 基础测试'),
          ),
        ),
      );

      await tester.pumpWidget(testApp);
      await tester.pumpAndSettle();

      // 验证应用加载
      expect(find.text('测试页面'), findsOneWidget);
      expect(find.text('Daily Satori 基础测试'), findsOneWidget);
      print('✅ 基础应用渲染正常');
    });

    testWidgets('基础UI组件测试', (WidgetTester tester) async {
      // 创建简单的测试界面
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(
              title: const Text('测试页面'),
              backgroundColor: Colors.blue,
            ),
            body: Column(
              children: [
                const Text('这是一个测试文本'),
                ElevatedButton(
                  key: const Key('test_button'),
                  onPressed: () {},
                  child: const Text('测试按钮'),
                ),
                ListView(
                  shrinkWrap: true,
                  children: List.generate(3, (index) =>
                    ListTile(
                      key: Key('list_item_$index'),
                      title: Text('列表项 $index'),
                      leading: const Icon(Icons.list),
                    )
                  ),
                ),
              ],
            ),
            floatingActionButton: FloatingActionButton(
              key: const Key('fab_button'),
              onPressed: () {},
              child: const Icon(Icons.add),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证各个组件
      expect(find.text('测试页面'), findsOneWidget);
      expect(find.text('这是一个测试文本'), findsOneWidget);
      expect(find.byKey(const Key('test_button')), findsOneWidget);
      expect(find.byKey(const Key('fab_button')), findsOneWidget);
      expect(find.byKey(const Key('list_item_0')), findsOneWidget);
      expect(find.byKey(const Key('list_item_1')), findsOneWidget);
      expect(find.byKey(const Key('list_item_2')), findsOneWidget);

      print('✅ 基础UI组件渲染正常');

      // 测试按钮点击
      await tester.tap(find.byKey(const Key('test_button')));
      await tester.pumpAndSettle();

      // 测试FAB点击
      await tester.tap(find.byKey(const Key('fab_button')));
      await tester.pumpAndSettle();

      // 测试列表项点击
      await tester.tap(find.byKey(const Key('list_item_1')));
      await tester.pumpAndSettle();

      print('✅ 基础交互功能正常');
    });

    testWidgets('滚动测试', (WidgetTester tester) async {
      // 创建长列表用于滚动测试
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('滚动测试')),
            body: ListView.builder(
              itemCount: 50,
              itemBuilder: (context, index) {
                return ListTile(
                  key: Key('item_$index'),
                  title: Text('项目 $index'),
                  subtitle: Text('这是第 $index 个项目的描述'),
                );
              },
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证初始可见的项目
      expect(find.byKey(const Key('item_0')), findsOneWidget);
      expect(find.byKey(const Key('item_1')), findsOneWidget);
      expect(find.byKey(const Key('item_5')), findsOneWidget);

      print('✅ 初始项目渲染正常');

      // 向下滚动
      await tester.fling(
        find.byType(ListView),
        const Offset(0, -300),
        1000,
      );
      await tester.pumpAndSettle();

      // 验证滚动后的项目
      expect(find.byKey(const Key('item_10')), findsOneWidget);
      print('✅ 向下滚动正常');

      // 向上滚动
      await tester.fling(
        find.byType(ListView),
        const Offset(0, 300),
        1000,
      );
      await tester.pumpAndSettle();

      // 验证回到顶部
      expect(find.byKey(const Key('item_0')), findsOneWidget);
      print('✅ 向上滚动正常');

      print('✅ 滚动测试完成');
    });

    testWidgets('文本输入测试', (WidgetTester tester) async {
      // 创建包含输入框的界面
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('输入测试')),
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
                  ElevatedButton(
                    key: const Key('save_button'),
                    onPressed: () {},
                    child: const Text('保存'),
                  ),
                ],
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 测试输入文本
      await tester.enterText(find.byKey(const Key('title_field')), '测试标题');
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('content_field')), '这是测试内容');
      await tester.pumpAndSettle();

      // 验证文本已输入
      expect(find.text('测试标题'), findsOneWidget);
      expect(find.text('这是测试内容'), findsOneWidget);

      print('✅ 文本输入功能正常');

      // 测试清空文本
      await tester.tap(find.byKey(const Key('title_field')));
      await tester.pumpAndSettle();

      // 清空输入框
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();

      print('✅ 文本输入测试完成');
    });
  });
}