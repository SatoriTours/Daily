# 工具类说明文档

本项目使用了以下工具类来组织辅助功能：

## 工具类结构

- **AppInfoUtils**: 应用信息相关工具
  - 获取应用版本信息
  - 获取应用包名
  - 获取应用名称

- **ClipboardUtils**: 剪贴板操作工具
  - 获取剪贴板文本
  - 设置剪贴板文本

- **DateTimeUtils**: 日期和时间处理工具
  - 获取当前时间的ISO格式
  - 更新数据的时间戳
  - 格式化日期时间为本地格式

- **DialogUtils**: 对话框显示工具
  - 显示提示对话框
  - 显示确认对话框
  - 显示输入对话框

- **StringUtils**: 字符串处理工具
  - 检查文本是否包含中文
  - 获取文本的子串
  - 获取文本的第一行
  - 从主机名获取顶级域名
  - 从文本中提取URL

- **UIUtils**: UI相关工具
  - 显示成功提示
  - 显示错误提示
  - 显示加载提示
  - 显示确认对话框

## 如何使用

有两种方式导入这些工具类：

### 1. 直接导入特定工具类

```dart
import 'package:daily_satori/app/utils/string_utils.dart';

// 使用示例
void example() {
  final hasChineseChars = StringUtils.isChinese('你好');
}
```

### 2. 使用集中导出文件

```dart
import 'package:daily_satori/app/utils/utils.dart';

// 使用示例
void example() {
  final hasChineseChars = StringUtils.isChinese('你好');
  UIUtils.showSuccess('操作成功');
}
```

### 3. 通过全局导出文件（向后兼容）

```dart
import 'package:daily_satori/global.dart';

// 使用示例（新代码中建议直接使用工具类）
void example() {
  // 旧方式（仍支持但不推荐）
  final hasChineseChars = isChinese('你好');
  successNotice('操作成功');

  // 新方式（推荐）
  final hasChineseChars2 = StringUtils.isChinese('你好');
  UIUtils.showSuccess('操作成功');
}
```

## 最佳实践

- 对于新代码，建议直接使用工具类中的方法，而不是使用全局函数
- 对于工具类中的方法，所有必要的参数都应该通过命名参数传递
- 所有工具类都使用私有构造函数，防止实例化
- 工具类方法应该是静态的，便于调用
