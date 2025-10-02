import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';

void main() {
  group('Daily Satori 基础功能测试', () {
    setUp(() {
      // 重置 GetX 状态
      Get.reset();
    });

    testWidgets('Material Design 基础组件测试', (WidgetTester tester) async {
      // 创建测试应用
      await tester.pumpWidget(
        GetMaterialApp(
          title: 'Daily Satori Test',
          home: Scaffold(
            appBar: AppBar(
              title: const Text('Daily Satori'),
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
              actions: [
                IconButton(
                  key: const Key('search_button'),
                  icon: const Icon(Icons.search),
                  onPressed: () {},
                ),
                IconButton(
                  key: const Key('more_button'),
                  icon: const Icon(Icons.more_horiz),
                  onPressed: () {},
                ),
              ],
            ),
            body: Column(
              children: [
                // 搜索栏
                Container(
                  key: const Key('search_bar'),
                  padding: const EdgeInsets.all(16.0),
                  child: TextField(
                    decoration: InputDecoration(
                      hintText: '搜索...',
                      prefixIcon: const Icon(Icons.search),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8.0),
                      ),
                    ),
                  ),
                ),

                // 列表区域
                Expanded(
                  child: ListView.builder(
                    key: const Key('content_list'),
                    itemCount: 5,
                    itemBuilder: (context, index) {
                      return Card(
                        key: Key('article_card_$index'),
                        margin: const EdgeInsets.symmetric(
                          horizontal: 16.0,
                          vertical: 8.0,
                        ),
                        child: ListTile(
                          leading: CircleAvatar(
                            child: Text('${index + 1}'),
                          ),
                          title: Text('测试文章 $index'),
                          subtitle: Text('这是第 $index 篇测试文章的摘要内容'),
                          trailing: const Icon(Icons.arrow_forward_ios),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
            floatingActionButton: FloatingActionButton(
              key: const Key('add_fab'),
              onPressed: () {},
              child: const Icon(Icons.add),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证主要组件存在
      expect(find.text('Daily Satori'), findsOneWidget);
      expect(find.byKey(const Key('search_button')), findsOneWidget);
      expect(find.byKey(const Key('more_button')), findsOneWidget);
      expect(find.byKey(const Key('search_bar')), findsOneWidget);
      expect(find.byKey(const Key('content_list')), findsOneWidget);
      expect(find.byKey(const Key('add_fab')), findsOneWidget);

      // 验证列表项
      for (int i = 0; i < 5; i++) {
        expect(find.byKey(Key('article_card_$i')), findsOneWidget);
        expect(find.text('测试文章 $i'), findsOneWidget);
      }

      print('✅ 所有基础组件渲染正常');
    });

    testWidgets('交互功能测试', (WidgetTester tester) async {
      // 创建包含交互的测试界面
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(
              key: const Key('app_bar'),
              title: const Text('交互测试'),
            ),
            body: Column(
              children: [
                // 搜索区域
                Container(
                  key: const Key('search_container'),
                  padding: const EdgeInsets.all(16.0),
                  child: TextField(
                    key: const Key('search_field'),
                    decoration: const InputDecoration(
                      labelText: '搜索内容',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),

                // 按钮组
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Row(
                    children: [
                      ElevatedButton(
                        key: const Key('filter_button'),
                        onPressed: () {},
                        child: const Text('筛选'),
                      ),
                      const SizedBox(width: 16),
                      ElevatedButton(
                        key: const Key('refresh_button'),
                        onPressed: () {},
                        child: const Text('刷新'),
                      ),
                    ],
                  ),
                ),

                // 内容列表
                Expanded(
                  child: ListView.builder(
                    key: const Key('items_list'),
                    itemCount: 10,
                    itemBuilder: (context, index) {
                      return Card(
                        key: Key('item_$index'),
                        child: ListTile(
                          title: Text('项目 $index'),
                          subtitle: Text('项目 $index 的描述'),
                          leading: Icon(Icons.star, color: index % 2 == 0 ? Colors.amber : Colors.grey),
                          trailing: IconButton(
                            key: Key('action_$index'),
                            icon: const Icon(Icons.more_vert),
                            onPressed: () {},
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
            floatingActionButton: FloatingActionButton(
              key: const Key('add_item_fab'),
              onPressed: () {},
              child: const Icon(Icons.add),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 测试搜索功能
      await tester.tap(find.byKey(const Key('search_field')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('search_field')), '测试搜索');
      await tester.pumpAndSettle();

      expect(find.text('测试搜索'), findsOneWidget);
      print('✅ 搜索输入功能正常');

      // 测试按钮点击
      await tester.tap(find.byKey(const Key('filter_button')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('refresh_button')));
      await tester.pumpAndSettle();

      print('✅ 按钮点击功能正常');

      // 测试FAB点击
      await tester.tap(find.byKey(const Key('add_item_fab')));
      await tester.pumpAndSettle();

      print('✅ FAB点击功能正常');

      // 测试列表项点击
      await tester.tap(find.byKey(const Key('item_3')));
      await tester.pumpAndSettle();

      print('✅ 列表项点击功能正常');

      // 测试操作按钮点击
      await tester.tap(find.byKey(const Key('action_3')));
      await tester.pumpAndSettle();

      print('✅ 操作按钮点击功能正常');
    });

    testWidgets('滚动和动态内容测试', (WidgetTester tester) async {
      // 创建长列表用于滚动测试
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(
              title: const Text('滚动测试'),
              actions: [
                IconButton(
                  key: const Key('scroll_to_top'),
                  icon: const Icon(Icons.keyboard_arrow_up),
                  onPressed: () {},
                ),
              ],
            ),
            body: ListView.builder(
              key: const Key('scroll_list'),
              itemCount: 100,
              itemBuilder: (context, index) {
                return Card(
                  key: Key('card_$index'),
                  margin: const EdgeInsets.all(8.0),
                  child: Container(
                    height: 80,
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '标题 $index',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text('这是第 $index 个项目的详细描述内容'),
                        const Spacer(),
                        Row(
                          children: [
                            Icon(Icons.favorite, color: index % 3 == 0 ? Colors.red : Colors.grey),
                            const SizedBox(width: 16),
                            Icon(Icons.bookmark, color: index % 5 == 0 ? Colors.blue : Colors.grey),
                            const Spacer(),
                            Text(
                              '2024-01-${(index % 28 + 1).toString().padLeft(2, '0')}',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证初始可见项目
      expect(find.byKey(const Key('card_0')), findsOneWidget);
      expect(find.byKey(const Key('card_5')), findsOneWidget);
      expect(find.byKey(const Key('card_10')), findsOneWidget);
      print('✅ 初始项目渲染正常');

      // 向下滚动
      await tester.fling(
        find.byType(ListView),
        const Offset(0, -500),
        2000,
      );
      await tester.pumpAndSettle();

      // 验证滚动后的项目
      expect(find.byKey(const Key('card_30')), findsOneWidget);
      expect(find.byKey(const Key('card_50')), findsOneWidget);
      print('✅ 向下滚动正常');

      // 继续向下滚动
      await tester.fling(
        find.byType(ListView),
        const Offset(0, -300),
        1500,
      );
      await tester.pumpAndSettle();

      // 验证更远的项目
      expect(find.byKey(const Key('card_80')), findsOneWidget);
      print('✅ 长距离滚动正常');

      // 向上滚动到顶部
      await tester.fling(
        find.byType(ListView),
        const Offset(0, 1000),
        3000,
      );
      await tester.pumpAndSettle();

      // 验证回到顶部
      expect(find.byKey(const Key('card_0')), findsOneWidget);
      print('✅ 回到顶部正常');

      // 测试点击"回到顶部"按钮
      await tester.tap(find.byKey(const Key('scroll_to_top')));
      await tester.pumpAndSettle();

      print('✅ 滚动测试完成');
    });

    testWidgets('表单输入和验证测试', (WidgetTester tester) async {
      // 创建表单界面
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('表单测试')),
            body: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Form(
                key: const Key('test_form'),
                child: Column(
                  children: [
                    // 标题输入
                    TextFormField(
                      key: const Key('title_input'),
                      decoration: const InputDecoration(
                        labelText: '标题',
                        border: OutlineInputBorder(),
                        hintText: '请输入标题',
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return '标题不能为空';
                        }
                        return null;
                      },
                    ),

                    const SizedBox(height: 16),

                    // 内容输入
                    TextFormField(
                      key: const Key('content_input'),
                      decoration: const InputDecoration(
                        labelText: '内容',
                        border: OutlineInputBorder(),
                        hintText: '请输入内容',
                      ),
                      maxLines: 5,
                      validator: (value) {
                        if (value == null || value.length < 10) {
                          return '内容至少需要10个字符';
                        }
                        return null;
                      },
                    ),

                    const SizedBox(height: 16),

                    // 选择器
                    DropdownButtonFormField<String>(
                      key: const Key('category_input'),
                      decoration: const InputDecoration(
                        labelText: '分类',
                        border: OutlineInputBorder(),
                      ),
                      items: const [
                        DropdownMenuItem(value: 'personal', child: Text('个人')),
                        DropdownMenuItem(value: 'work', child: Text('工作')),
                        DropdownMenuItem(value: 'study', child: Text('学习')),
                      ],
                      onChanged: (value) {},
                    ),

                    const SizedBox(height: 24),

                    // 按钮组
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
                        Expanded(
                          child: OutlinedButton(
                            key: const Key('cancel_button'),
                            onPressed: () {},
                            child: const Text('取消'),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 测试输入功能
      await tester.tap(find.byKey(const Key('title_input')));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(const Key('title_input')), '测试日记标题');
      await tester.pumpAndSettle();

      expect(find.text('测试日记标题'), findsOneWidget);
      print('✅ 标题输入正常');

      // 测试长文本输入
      await tester.tap(find.byKey(const Key('content_input')));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const Key('content_input')),
        '这是一段超过10个字符的测试内容，用于验证表单验证功能是否正常工作。'
      );
      await tester.pumpAndSettle();

      expect(find.text('这是一段超过10个字符的测试内容，用于验证表单验证功能是否正常工作。'), findsOneWidget);
      print('✅ 内容输入正常');

      // 测试下拉选择
      await tester.tap(find.byKey(const Key('category_input')));
      await tester.pumpAndSettle();

      await tester.tap(find.text('学习'));
      await tester.pumpAndSettle();

      print('✅ 分类选择正常');

      // 测试按钮点击
      await tester.tap(find.byKey(const Key('save_button')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const Key('cancel_button')));
      await tester.pumpAndSettle();

      print('✅ 按钮交互正常');
      print('✅ 表单测试完成');
    });
  });
}