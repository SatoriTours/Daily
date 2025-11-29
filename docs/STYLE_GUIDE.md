# Daily Satori 样式系统快速参考

> 本文档提供样式系统的快速查询参考，详细规范请参考 [CLAUDE.md](./CLAUDE.md#样式系统规范)

## 🎨 核心原则

1. **始终使用** `import 'package:daily_satori/app/styles/index.dart';`
2. **永不硬编码** 数值、颜色、字体样式
3. **优先使用** StyleGuide > 组件样式 > 基础 Tokens
4. **主题感知** 所有颜色和阴影必须适配主题

## 📦 快速导入

```dart
import 'package:daily_satori/app/styles/index.dart';
// 一次导入所有样式类
```

## 🎯 常用样式速查表

### 颜色 (AppColors)

```dart
// 主色系
AppColors.getPrimary(context)                    // 主色
AppColors.getSurface(context)                    // 表面色
AppColors.getBackground(context)                 // 背景色

// 容器色
AppColors.getSurfaceContainer(context)           // 容器背景
AppColors.getSurfaceContainerHighest(context)    // 高亮容器

// 文本色
AppColors.getOnSurface(context)                  // 主文本
AppColors.getOnSurfaceVariant(context)           // 次要文本

// 边框色
AppColors.getOutline(context)                    // 主边框
AppColors.getOutlineVariant(context)             // 次要边框

// 功能色
AppColors.getSuccess(context)                    // 成功
AppColors.getError(context)                      // 错误
AppColors.getWarning(context)                    // 警告
AppColors.getInfo(context)                       // 信息
```

### 间距 (Dimensions)

```dart
// 间距常量
Dimensions.spacingXs      // 4px
Dimensions.spacingS       // 8px
Dimensions.spacingM       // 16px
Dimensions.spacingL       // 24px
Dimensions.spacingXl      // 32px
Dimensions.spacingXxl     // 48px

// 内边距预设
Dimensions.paddingPage         // 页面内边距
Dimensions.paddingCard         // 卡片内边距
Dimensions.paddingButton       // 按钮内边距
Dimensions.paddingInput        // 输入框内边距
Dimensions.paddingListItem     // 列表项内边距

// 间隔组件
Dimensions.verticalSpacerXs    // 垂直 4px
Dimensions.verticalSpacerS     // 垂直 8px
Dimensions.verticalSpacerM     // 垂直 16px
Dimensions.verticalSpacerL     // 垂直 24px
Dimensions.verticalSpacerXl    // 垂直 32px

Dimensions.horizontalSpacerS   // 水平 8px
Dimensions.horizontalSpacerM   // 水平 16px
Dimensions.horizontalSpacerL   // 水平 24px
```

### 圆角 (Dimensions)

```dart
BorderRadius.circular(Dimensions.radiusXs)       // 4px
BorderRadius.circular(Dimensions.radiusS)        // 8px
BorderRadius.circular(Dimensions.radiusM)        // 12px
BorderRadius.circular(Dimensions.radiusL)        // 16px
BorderRadius.circular(Dimensions.radiusXl)       // 20px
BorderRadius.circular(Dimensions.radiusCircular) // 圆形
```

### 图标尺寸 (Dimensions)

```dart
Icon(Icons.star, size: Dimensions.iconSizeXs)    // 16px
Icon(Icons.star, size: Dimensions.iconSizeS)     // 18px
Icon(Icons.star, size: Dimensions.iconSizeM)     // 20px
Icon(Icons.star, size: Dimensions.iconSizeL)     // 24px
Icon(Icons.star, size: Dimensions.iconSizeXl)    // 32px
Icon(Icons.star, size: Dimensions.iconSizeXxl)   // 48px
```

### 字体样式 (AppTypography)

```dart
// 标题系列
AppTypography.headingLarge     // 32px, w600 - 页面主标题
AppTypography.headingMedium    // 24px, w600 - 区块标题
AppTypography.headingSmall     // 20px, w600 - 小节标题

// 副标题系列
AppTypography.titleLarge       // 18px, w600 - 卡片标题
AppTypography.titleMedium      // 16px, w600 - 列表标题
AppTypography.titleSmall       // 14px, w500 - 小标题/标签

// 正文系列
AppTypography.bodyLarge        // 16px, w400 - 大正文
AppTypography.bodyMedium       // 15px, w400 - 标准正文
AppTypography.bodySmall        // 13px, w400 - 小正文/说明

// 标签系列
AppTypography.labelLarge       // 14px, w500
AppTypography.labelMedium      // 12px, w500
AppTypography.labelSmall       // 11px, w500

// 特殊用途
AppTypography.buttonText       // 按钮文本
AppTypography.appBarTitle      // AppBar标题
AppTypography.chipText         // 标签文本
```

### 透明度 (Opacities)

```dart
Opacities.extraLow      // 0.05 (5%)
Opacities.low           // 0.1  (10%)
Opacities.medium        // 0.2  (20%)
Opacities.mediumHigh    // 0.25 (25%)
Opacities.high          // 0.3  (30%)
Opacities.half          // 0.5  (50%)
Opacities.mediumOpaque  // 0.8  (80%)
```

### 阴影 (AppShadows)

```dart
AppShadows.getXsShadow(context)    // 极小阴影
AppShadows.getSShadow(context)     // 小阴影
AppShadows.getMShadow(context)     // 中等阴影 - 卡片
AppShadows.getLShadow(context)     // 大阴影 - 对话框
AppShadows.getXlShadow(context)    // 特大阴影 - 模态框
```

### 边框宽度 (Dimensions)

```dart
// 边框宽度常量
Dimensions.borderWidthXs   // 0.5px - 极细边框
Dimensions.borderWidthS    // 1.0px - 细边框
Dimensions.borderWidthM    // 1.5px - 中等边框
Dimensions.borderWidthL    // 2.0px - 粗边框
Dimensions.borderWidthXl   // 4.0px - 极粗边框
```

### 边框方法 (AppBorders)

```dart
// 主题感知边框
AppBorders.getBaseBorder(context)      // 基本边框（自动适应主题）
AppBorders.getPrimaryBorder(context)   // 主题色边框
AppBorders.getCardBorder(context)      // 卡片边框

// 单边边框（需手动指定颜色）
AppBorders.getTopBorder(color, opacity: 0.3)     // 顶部边框
AppBorders.getBottomBorder(color, opacity: 0.3)  // 底部边框
AppBorders.getLeftBorder(color, opacity: 0.3)    // 左侧边框
AppBorders.getRightBorder(color, opacity: 0.3)   // 右侧边框

// 输入框边框
AppBorders.getInputBorder(context)         // 默认状态
AppBorders.getInputFocusedBorder(context)  // 聚焦状态
AppBorders.getInputErrorBorder(context)    // 错误状态

// 分隔线
AppBorders.getDivider(context)             // 水平分隔线
AppBorders.getVerticalDivider(context)     // 垂直分隔线
```

## 🔧 组件样式速查

### 按钮 (ButtonStyles)

```dart
// 主要按钮
ElevatedButton(
  style: ButtonStyles.getPrimaryStyle(context),
  child: Text('确认', style: AppTypography.buttonText),
)

// 次要按钮
ElevatedButton(
  style: ButtonStyles.getSecondaryStyle(context),
  child: Text('取消', style: AppTypography.buttonText),
)

// 轮廓按钮
OutlinedButton(
  style: ButtonStyles.getOutlinedStyle(context),
  child: Text('了解更多', style: AppTypography.buttonText),
)

// 文本按钮
TextButton(
  style: ButtonStyles.getTextStyle(context),
  child: Text('跳过', style: AppTypography.buttonText),
)

// 危险按钮
ElevatedButton(
  style: ButtonStyles.getDangerStyle(context),
  child: Text('删除', style: AppTypography.buttonText),
)
```

### 输入框 (InputStyles)

```dart
// 标准输入框
TextField(
  decoration: InputStyles.getInputDecoration(
    context,
    hintText: '请输入内容',
  ),
)

// 搜索框
TextField(
  decoration: InputStyles.getSearchDecoration(
    context,
    hintText: '搜索...',
  ),
)

// 无边框输入框
TextField(
  decoration: InputStyles.getCleanInputDecoration(
    context,
    hintText: '记录...',
  ),
)

// 标题输入框
TextField(
  decoration: InputStyles.getTitleInputDecoration(
    context,
    hintText: '标题',
  ),
)
```

## 🎯 StyleGuide 高级应用

### 容器装饰

```dart
// 页面容器
Container(
  decoration: StyleGuide.getPageContainerDecoration(context),
)

// 卡片
Container(
  decoration: StyleGuide.getCardDecoration(context),
)

// 列表项
Container(
  decoration: StyleGuide.getListItemDecoration(context),
)
```

### 状态组件

```dart
// 空状态
StyleGuide.getEmptyState(
  context,
  message: '暂无数据',
  icon: Icons.inbox_outlined,
)

// 加载状态
StyleGuide.getLoadingState(context)

// 错误状态
StyleGuide.getErrorState(
  context,
  message: '加载失败',
  onRetry: onRetry,
)
```

### 布局模板

```dart
// 标准页面
StyleGuide.getStandardPageLayout(
  context: context,
  child: content,
)

// 列表布局
StyleGuide.getStandardListLayout(
  context: context,
  children: items,
)

// 网格布局
StyleGuide.getStandardGridLayout(
  context: context,
  children: items,
  crossAxisCount: 2,
)
```

## ✅ 最佳实践示例

### 完整页面示例

```dart
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/index.dart';

class ExampleView extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.getBackground(context),
      appBar: AppBar(
        title: Text('示例', style: AppTypography.appBarTitle),
      ),
      body: SingleChildScrollView(
        padding: Dimensions.paddingPage,
        child: Column(
          children: [
            // 标题
            Text('标题', style: AppTypography.headingMedium),
            Dimensions.verticalSpacerS,

            // 描述
            Text(
              '描述文字',
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.getOnSurfaceVariant(context),
              ),
            ),
            Dimensions.verticalSpacerL,

            // 卡片
            Container(
              padding: Dimensions.paddingCard,
              decoration: StyleGuide.getCardDecoration(context),
              child: Column(
                children: [
                  Text('卡片标题', style: AppTypography.titleMedium),
                  Dimensions.verticalSpacerS,
                  Text('卡片内容', style: AppTypography.bodyMedium),
                ],
              ),
            ),
            Dimensions.verticalSpacerL,

            // 输入框
            TextField(
              decoration: InputStyles.getInputDecoration(
                context,
                hintText: '输入内容',
              ),
            ),
            Dimensions.verticalSpacerL,

            // 按钮
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    style: ButtonStyles.getOutlinedStyle(context),
                    onPressed: () {},
                    child: Text('取消', style: AppTypography.buttonText),
                  ),
                ),
                Dimensions.horizontalSpacerM,
                Expanded(
                  child: ElevatedButton(
                    style: ButtonStyles.getPrimaryStyle(context),
                    onPressed: () {},
                    child: Text('确认', style: AppTypography.buttonText),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
```

## ❌ 常见错误示例

### 错误 1：硬编码数值

```dart
// ❌ 错误
padding: const EdgeInsets.all(16)
SizedBox(height: 20)
BorderRadius.circular(10)

// ✅ 正确
padding: Dimensions.paddingCard
Dimensions.verticalSpacerL
BorderRadius.circular(Dimensions.radiusS)
```

### 错误 2：硬编码颜色

```dart
// ❌ 错误
color: Color(0xFF5E8BFF)
color: Colors.blue
color: Color.fromRGBO(94, 139, 255, 1.0)

// ✅ 正确
color: AppColors.getPrimary(context)
color: AppColors.getSurface(context)
```

### 错误 3：硬编码字体

```dart
// ❌ 错误
style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)
style: TextStyle(fontSize: 14)

// ✅ 正确
style: AppTypography.titleMedium
style: AppTypography.bodyMedium
```

### 错误 4：手动判断主题

```dart
// ❌ 错误
final isDark = Theme.of(context).brightness == Brightness.dark;
color: isDark ? Colors.white : Colors.black

// ✅ 正确
color: AppColors.getOnSurface(context)
```

### 错误 5：过度使用 copyWith

```dart
// ❌ 错误：修改了太多属性
style: AppTypography.bodyMedium.copyWith(
  fontSize: 14,
  fontWeight: FontWeight.w500,
  height: 1.3,
  letterSpacing: 0.5,
)

// ✅ 正确：使用预定义样式或仅修改颜色
style: AppTypography.titleSmall
// 或
style: AppTypography.bodyMedium.copyWith(
  color: AppColors.getPrimary(context),
)
```

## 📝 检查清单

在提交代码前，确保：

- [ ] 使用 `import 'package:daily_satori/app/styles/index.dart';`
- [ ] 没有硬编码的数值（EdgeInsets, SizedBox, double值）
- [ ] 没有硬编码的颜色（Color(), Colors.xxx）
- [ ] 没有硬编码的字体样式（TextStyle()）
- [ ] 使用 AppColors.getXxx(context) 获取颜色
- [ ] 使用 Dimensions 常量设置尺寸
- [ ] 使用 AppTypography 预定义字体样式
- [ ] 优先使用 StyleGuide 高级方法
- [ ] 按钮使用 ButtonStyles
- [ ] 输入框使用 InputStyles

## 🔗 参考资源

- [CLAUDE.md - 完整样式系统规范](./CLAUDE.md#样式系统规范)
- [样式系统源码](./lib/app/styles/)
- [StyleGuide 文档](./lib/app/styles/style_guide.dart)

---

**记住**：样式系统的目标是创建一致、可维护、美观的用户界面。当你发现需要重复使用的样式时，请添加到样式系统中，而不是复制代码！
