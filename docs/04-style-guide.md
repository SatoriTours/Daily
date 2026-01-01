# 🎨 Daily Satori 样式系统参考

> 统一导入：`import 'package:daily_satori/app/styles/index.dart';`

## 🎯 核心原则

1. **禁止硬编码** - 颜色、间距、字体
2. **主题感知** - 使用 `AppColors.getXxx(context)`
3. **优先级**：`StyleGuide` > `ButtonStyles` > `Dimensions`

## 🎨 颜色 (AppColors)

```dart
AppColors.getPrimary(context)              // 主色
AppColors.getSurface(context)              // 表面色
AppColors.getBackground(context)           // 背景色
AppColors.getOnSurface(context)            // 主文本
AppColors.getOnSurfaceVariant(context)     // 次要文本
AppColors.getOutline(context)              // 边框色
AppColors.getSuccess/Error/Warning(context) // 功能色
```

## 📐 间距 (Dimensions)

```dart
// 间距常量
Dimensions.spacingXs/S/M/L/Xl/Xxl  // 4/8/16/24/32/48px

// 内边距预设
Dimensions.paddingPage/Card/Button/Input/ListItem

// 间隔组件
Dimensions.verticalSpacerXs/S/M/L/Xl
Dimensions.horizontalSpacerS/M/L

// 圆角
Dimensions.radiusXs/S/M/L/Xl/Circular  // 4/8/12/16/20/圆形

// 图标尺寸
Dimensions.iconSizeXs/S/M/L/Xl/Xxl  // 16/18/20/24/32/48px
```

## 📝 字体 (AppTypography)

```dart
// 标题
AppTypography.headingLarge/Medium/Small  // 32/24/20px

// 副标题
AppTypography.titleLarge/Medium/Small    // 18/16/14px

// 正文
AppTypography.bodyLarge/Medium/Small     // 16/15/13px

// 标签
AppTypography.labelLarge/Medium/Small    // 14/12/11px

// 特殊
AppTypography.buttonText/appBarTitle/chipText
```

## 🔘 按钮 (ButtonStyles)

```dart
ButtonStyles.getPrimaryStyle(context)    // 主要按钮
ButtonStyles.getSecondaryStyle(context)  // 次要按钮
ButtonStyles.getOutlinedStyle(context)   // 轮廓按钮
ButtonStyles.getTextStyle(context)       // 文本按钮
ButtonStyles.getDangerStyle(context)     // 危险按钮
```

## 🃏 卡片 (CardStyles)

```dart
CardStyles.getStandardDecoration(context)  // 标准卡片
CardStyles.getFlatDecoration(context)      // 无阴影卡片
CardStyles.getSimpleDecoration(context)    // 简洁卡片
CardStyles.getAccentDecoration(context)    // 强调卡片
```

## 📝 输入框 (InputStyles)

```dart
InputStyles.getInputDecoration(context, hintText: '...')
InputStyles.getSearchDecoration(context, hintText: '...')
InputStyles.getCleanInputDecoration(context, hintText: '...')
```

## 🎯 StyleGuide 高级

```dart
// 状态组件
StyleGuide.getEmptyState(context, message: '...', icon: Icons.inbox)
StyleGuide.getLoadingState(context)
StyleGuide.getErrorState(context, message: '...', onRetry: ...)

// 装饰
StyleGuide.getPageContainerDecoration(context)
StyleGuide.getCardDecoration(context)
StyleGuide.getListItemDecoration(context)
```

## ❌ 禁止示例

```dart
// ❌ 禁止硬编码
Color(0xFF5E8BFF)
EdgeInsets.all(16)
TextStyle(fontSize: 14)
BorderRadius.circular(8)

// ✅ 正确方式
AppColors.getPrimary(context)
Dimensions.paddingCard
AppTypography.bodyMedium
BorderRadius.circular(Dimensions.radiusS)
```
