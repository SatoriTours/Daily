# 工具类说明文档

本目录包含项目的所有工具类和基础控制器。

## 目录结构

### 基础控制器
- **base_controller.dart**: GetX 控制器基类（含状态管理、加载状态、错误处理、导航）
- **base_list_controller.dart**: 列表控制器基类（含分页、搜索、过滤）

### 字符串与文本处理
- **string_utils.dart**: 字符串工具类
  - `StringUtils`: 静态方法类（中文检测、子串截取、URL提取等）
- **i18n_extension.dart**: 国际化扩展，提供 `.t` 翻译方法

### UI 与交互
- **ui_utils.dart**: UI 工具类（Snackbar、成功/错误提示）
- **dialog_utils.dart**: 对话框工具类（提示框、确认框、输入框、加载框）

### 系统与设备
- **app_info_utils.dart**: 应用信息工具类（版本号、包名、环境判断）

### 数据处理
- **date_time_utils.dart**: 日期时间工具类（格式化、时间戳处理）
- **random_utils.dart**: 随机工具类（生成随机ID、密码）

## 使用方式

### 推荐方式：通过 app_exports.dart 导入
```dart
import 'package:daily_satori/app_exports.dart';

// 所有工具类都可直接使用
void example() {
  final hasChineseChars = StringUtils.isChinese('你好');
  UIUtils.showSuccess('操作成功');
  'button.save'.t; // i18n 翻译
}
```

### 按需导入特定工具
```dart
import 'package:daily_satori/app/utils/string_utils.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
```

### 使用统一导出文件
```dart
import 'package:daily_satori/app/utils/utils.dart';
```

## 最佳实践

1. **新代码推荐使用 `app_exports.dart`**，它已导出所有常用工具
2. **工具类使用静态方法**，便于调用且无需实例化
3. **控制器继承**：
   - 简单控制器继承 `BaseController`
   - 需要状态管理的控制器继承 `BaseGetXController`
   - 列表页面控制器继承 `BaseListController`
4. **国际化**：使用 `'key'.t` 语法进行翻译
