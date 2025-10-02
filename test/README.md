# Daily Satori 集成测试

这个目录包含了 Daily Satori 应用的集成测试代码，用于验证应用的主要功能是否正常工作。

## 测试覆盖范围

### 1. 文章列表页面测试
- ✅ 验证文章列表能正常显示数据库中的文章
- ✅ 测试搜索功能
- ✅ 测试空状态显示

### 2. 文章详情页面测试
- ✅ 验证右上角菜单的刷新功能
- ✅ 测试下拉刷新功能
- ✅ 验证刷新状态指示器

### 3. 日记功能测试
- ✅ 测试新增日记功能
- ✅ 测试编辑日记功能
- ✅ 测试图片添加和显示功能
- ✅ 验证日记保存成功

### 4. 读书页面测试
- ✅ 测试添加书籍功能
- ✅ 测试刷新书籍列表功能
- ✅ 验证书籍信息显示

### 5. 设置页面AI模型测试
- ✅ 测试AI模型选择功能
- ✅ 测试API配置编辑功能
- ✅ 验证配置保存成功

### 6. 完整流程测试
- ✅ 测试从首页到各功能页面的导航
- ✅ 验证底部导航栏功能

## 文件结构

```
test/
├── integration_test.dart         # 主集成测试文件
├── test_config.dart             # 测试配置和工具类
├── image_picker_test_helper.dart # 图片选择测试辅助工具
├── widget_test.dart            # 基础widget测试文件
└── README.md                   # 本文件
```

## 运行测试

### 前置条件

1. 确保已安装 Flutter SDK
2. 确保设备或模拟器已连接
3. 确保项目依赖已安装

```bash
flutter pub get
```

### 运行所有集成测试

```bash
flutter test integration_test/integration_test.dart
```

### 运行特定测试

```bash
# 只运行文章列表测试
flutter test integration_test/integration_test.dart --name="文章列表页面"

# 只运行日记功能测试
flutter test integration_test/integration_test.dart --name="日记功能"

# 只运行AI配置测试
flutter test integration_test/integration_test.dart --name="AI模型修改"
```

### 生成测试覆盖率报告

```bash
flutter test --coverage
genhtml coverage/lcov.info -o coverage/html
```

## 测试配置说明

### TestKeys 类
包含所有测试中使用的 Widget Key，确保测试的稳定性和可维护性。

### TestUtils 类
提供常用的测试工具方法：
- `waitForPageLoad()` - 等待页面加载
- `findAndTap()` - 查找并点击控件
- `findAndEnterText()` - 查找并输入文本
- `performPullToRefresh()` - 执行下拉刷新

### TestData 类
包含测试中使用的静态数据，避免硬编码。

### ImagePickerTestHelper 类
专门处理图片选择功能的测试，包括：
- 模拟图片选择流程
- 验证图片预览
- 测试多图片管理

## 测试最佳实践

### 1. Widget Key 的使用
所有测试都依赖于预定义的 Widget Key，请在应用代码中确保这些 Key 的存在：

```dart
// 示例：文章列表项
ListView.builder(
  key: const Key('articles_list'),
  itemCount: articles.length,
  itemBuilder: (context, index) {
    return ListTile(
      key: Key('article_item_$index'),
      title: Text(
        articles[index].title,
        key: const Key('article_title'),
      ),
    );
  },
)
```

### 2. 异步操作处理
对于网络请求、数据库操作等异步操作，使用 `pumpAndSettle()` 等待操作完成：

```dart
await tester.pumpAndSettle(const Duration(seconds: 3));
```

### 3. 错误处理
测试包含了对各种错误情况的处理：
- 网络错误
- 数据库错误
- 用户输入错误
- 权限问题

### 4. Mock 数据
使用 `MockData` 类提供一致的测试数据：

```dart
final mockArticle = MockData.getMockArticle();
```

## 故障排除

### 常见问题

1. **测试超时**
   - 增加等待时间：`await tester.pumpAndSettle(const Duration(seconds: 5));`
   - 检查网络连接和模拟器性能

2. **Widget 找不到**
   - 确保应用代码中添加了相应的 Key
   - 检查 Widget 层级结构是否正确

3. **异步操作未完成**
   - 使用 `pumpAndSettle()` 等待异步操作
   - 检查 Future 是否正确处理

4. **权限问题**
   - 确保测试设备具有必要权限
   - 在测试前手动授予权限

### 调试技巧

1. 使用 `print()` 输出调试信息
2. 使用 `tester.takeScreenshot()` 保存截图
3. 检查 Widget 树结构：
   ```dart
   print(tester.binding.widgetTree);
   ```

## 持续集成

这些集成测试可以在 CI/CD 流水线中运行：

```yaml
# .github/workflows/test.yml
name: Flutter Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: subosito/flutter-action@v2
        with:
          flutter-version: '3.19.0'
      - run: flutter pub get
      - run: flutter test
      - run: flutter test integration_test/integration_test.dart
```

## 贡献指南

### 添加新的测试

1. 在 `TestKeys` 中添加新的 Key
2. 在 `integration_test.dart` 中添加新的测试用例
3. 在 `README.md` 中更新测试覆盖范围
4. 确保新测试通过所有环境

### 修改现有测试

1. 更新相应的 Key 和测试逻辑
2. 确保向后兼容性
3. 更新文档

## 联系方式

如有问题或建议，请联系开发团队或提交 Issue。