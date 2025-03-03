# 统一样式系统使用指南

本项目实现了统一的样式系统，用于确保整个应用的UI风格一致。样式系统包括尺寸、边距、颜色、字体和常用组件样式等。

## 导入方式

推荐使用统一导入方式：

```dart
import 'package:daily_satori/app/styles/index.dart';
```

这将导入所有样式相关的类，包括：`AppTheme`、`AppStyles`、`Dimensions`、`MyFontStyle`等。

## 使用指南

### 1. 使用主题

获取当前主题的颜色和文本样式：

```dart
// 获取当前主题的颜色方案
final colorScheme = AppTheme.getColorScheme(context);

// 使用颜色
Container(
  color: colorScheme.surface,
  child: Text('示例文本', style: TextStyle(color: colorScheme.onSurface)),
);

// 获取当前主题的文本主题
final textTheme = AppTheme.getTextTheme(context);

// 使用文本样式
Text('标题', style: textTheme.headlineSmall),
Text('正文', style: textTheme.bodyMedium),
```

### 2. 使用尺寸和间距

使用预定义的尺寸和间距，避免硬编码：

```dart
// 使用内边距
Container(
  padding: Dimensions.paddingM, // 等价于 EdgeInsets.all(16)
  child: Text('示例文本'),
);

// 使用垂直间距
Column(
  children: [
    Text('第一行'),
    Dimensions.verticalSpacerM, // 等价于 SizedBox(height: 16)
    Text('第二行'),
  ],
);

// 使用水平内边距和特定顶部内边距
Container(
  padding: Dimensions.paddingHorizontalM.copyWith(top: Dimensions.spacingL),
  child: Text('示例文本'),
);
```

### 3. 使用圆角和尺寸

统一边框圆角和组件尺寸：

```dart
// 使用圆角
Container(
  decoration: BoxDecoration(
    borderRadius: BorderRadius.circular(Dimensions.radiusM), // 12.0
  ),
);

// 使用图标尺寸
Icon(Icons.home, size: Dimensions.iconSizeM); // 24.0
```

### 4. 使用应用样式工具

使用`AppStyles`类可以快速应用一致的样式：

```dart
// 卡片装饰
Container(
  decoration: AppStyles.cardDecoration(context),
  child: Text('卡片内容'),
);

// 获取标准图标
AppStyles.getIcon(Icons.home, context, primary: true);

// 使用搜索框装饰
TextField(
  decoration: AppStyles.searchInputDecoration(context, hintText: '搜索...'),
);

// 使用空状态组件
if (items.isEmpty) {
  return AppStyles.emptyState(context, message: '暂无数据');
}

// 使用加载状态组件
if (isLoading) {
  return AppStyles.loadingState(context);
}
```

### 5. 样式替换对照表

以下是常见硬编码样式及其替换方式：

| 硬编码样式 | 推荐替换方式 |
|------------|-------------|
| `EdgeInsets.all(16)` | `Dimensions.paddingM` |
| `EdgeInsets.symmetric(horizontal: 16, vertical: 8)` | `Dimensions.paddingListItem` |
| `EdgeInsets.fromLTRB(20, 16, 20, 16)` | `Dimensions.paddingPage` |
| `BorderRadius.circular(8)` | `BorderRadius.circular(Dimensions.radiusS)` |
| `BorderRadius.circular(12)` | `BorderRadius.circular(Dimensions.radiusM)` |
| `SizedBox(height: 8)` | `Dimensions.verticalSpacerS` |
| `SizedBox(width: 16)` | `Dimensions.horizontalSpacerM` |
| `Icon(Icons.home, size: 24)` | `AppStyles.getIcon(Icons.home, context)` |
| `TextStyle(fontSize: 16, fontWeight: FontWeight.bold)` | `textTheme.titleMedium` |
| `BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12))` | `AppStyles.cardDecoration(context)` |

## 最佳实践

1. 不要硬编码尺寸、边距、圆角等数值，使用`Dimensions`类中的常量
2. 不要硬编码颜色，使用`AppTheme.getColorScheme(context)`获取主题颜色
3. 不要创建自定义的文本样式，尽量使用`AppTheme.getTextTheme(context)`
4. 对于常用组件（卡片、输入框、标签等），使用`AppStyles`类提供的预定义样式
5. 使用`Dimensions.verticalSpacerS/M/L`和`Dimensions.horizontalSpacerS/M/L`代替`SizedBox`
6. 对于多次重复的样式，考虑在`AppStyles`中添加新的方法
