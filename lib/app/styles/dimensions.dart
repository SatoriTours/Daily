import 'package:flutter/material.dart';

/// 应用尺寸常量类
/// 统一管理应用中的尺寸、边距、圆角等样式
class Dimensions {
  // 私有构造函数，防止实例化
  Dimensions._();

  // 边距常量
  /// 极小边距 - 4.0
  static const double spacingXs = 4.0;

  /// 小边距 - 8.0
  static const double spacingS = 8.0;

  /// 中等边距 - 16.0
  static const double spacingM = 16.0;

  /// 大边距 - 24.0
  static const double spacingL = 24.0;

  /// 极大边距 - 32.0
  static const double spacingXl = 32.0;

  /// 特大边距 - 48.0
  static const double spacingXxl = 48.0;

  // 常用内边距
  /// 无内边距
  static const EdgeInsets paddingNone = EdgeInsets.zero;

  /// 极小内边距 (4)
  static const EdgeInsets paddingXs = EdgeInsets.all(spacingXs);

  /// 小内边距 (8)
  static const EdgeInsets paddingS = EdgeInsets.all(spacingS);

  /// 中等内边距 (16)
  static const EdgeInsets paddingM = EdgeInsets.all(spacingM);

  /// 大内边距 (24)
  static const EdgeInsets paddingL = EdgeInsets.all(spacingL);

  /// 水平小内边距 (8, 0, 8, 0)
  static const EdgeInsets paddingHorizontalS = EdgeInsets.symmetric(horizontal: spacingS);

  /// 水平中等内边距 (16, 0, 16, 0)
  static const EdgeInsets paddingHorizontalM = EdgeInsets.symmetric(horizontal: spacingM);

  /// 水平大内边距 (24, 0, 24, 0)
  static const EdgeInsets paddingHorizontalL = EdgeInsets.symmetric(horizontal: spacingL);

  /// 垂直小内边距 (0, 8, 0, 8)
  static const EdgeInsets paddingVerticalS = EdgeInsets.symmetric(vertical: spacingS);

  /// 垂直中等内边距 (0, 16, 0, 16)
  static const EdgeInsets paddingVerticalM = EdgeInsets.symmetric(vertical: spacingM);

  /// 垂直大内边距 (0, 24, 0, 24)
  static const EdgeInsets paddingVerticalL = EdgeInsets.symmetric(vertical: spacingL);

  /// 常用表单内边距 (16, 12, 16, 12)
  static const EdgeInsets paddingForm = EdgeInsets.symmetric(horizontal: spacingM, vertical: 12);

  /// 列表项内边距 (16, 8, 16, 8)
  static const EdgeInsets paddingListItem = EdgeInsets.symmetric(horizontal: spacingM, vertical: spacingS);

  /// 卡片内边距 (16, 16, 16, 16)
  static const EdgeInsets paddingCard = EdgeInsets.all(spacingM);

  /// 搜索框内边距 (16, 0, 16, 0)
  static const EdgeInsets paddingSearchBar = EdgeInsets.symmetric(horizontal: spacingM);

  /// 页面内边距 (20, 16, 20, 16)
  static const EdgeInsets paddingPage = EdgeInsets.symmetric(horizontal: 20, vertical: spacingM);

  // 常用外边距
  /// 极小外边距 (4)
  static const EdgeInsets marginXs = EdgeInsets.all(spacingXs);

  /// 小外边距 (8)
  static const EdgeInsets marginS = EdgeInsets.all(spacingS);

  /// 中等外边距 (16)
  static const EdgeInsets marginM = EdgeInsets.all(spacingM);

  /// 卡片外边距 (16, 8, 16, 8)
  static const EdgeInsets marginCard = EdgeInsets.symmetric(horizontal: spacingM, vertical: spacingS);

  // 圆角常量
  /// 无圆角
  static const double radiusNone = 0.0;

  /// 极小圆角 - 4.0
  static const double radiusXs = 4.0;

  /// 小圆角 - 8.0
  static const double radiusS = 8.0;

  /// 中等圆角 - 12.0
  static const double radiusM = 12.0;

  /// 大圆角 - 16.0
  static const double radiusL = 16.0;

  /// 特大圆角 - 24.0
  static const double radiusXl = 24.0;

  /// 完全圆角（圆形）
  static const double radiusCircular = 100.0;

  // 高度和宽度常量
  /// 按钮高度 - 48.0
  static const double buttonHeight = 48.0;

  /// 小按钮高度 - 36.0
  static const double buttonHeightSmall = 36.0;

  /// 输入框高度 - 48.0
  static const double inputHeight = 48.0;

  /// 底部导航栏高度 - 56.0
  static const double bottomNavBarHeight = 56.0;

  /// 应用栏高度 - 56.0
  static const double appBarHeight = 56.0;

  /// 标准列表项高度 - 56.0
  static const double listItemHeight = 56.0;

  /// 密集列表项高度 - 48.0
  static const double listItemHeightDense = 48.0;

  /// 标签高度 - 32.0
  static const double chipHeight = 32.0;

  // 图标尺寸
  /// 极小图标尺寸 - 16.0
  static const double iconSizeXs = 16.0;

  /// 小图标尺寸 - 20.0
  static const double iconSizeS = 20.0;

  /// 中等图标尺寸 - 24.0（默认）
  static const double iconSizeM = 24.0;

  /// 大图标尺寸 - 32.0
  static const double iconSizeL = 32.0;

  /// 特大图标尺寸 - 48.0
  static const double iconSizeXl = 48.0;

  /// 占位图标尺寸 - 72.0
  static const double iconSizePlaceholder = 72.0;

  // 分隔符相关
  /// 分隔符高度 - 1.0
  static const double dividerHeight = 1.0;

  /// 分隔符缩进 - 16.0
  static const double dividerIndent = 16.0;

  /// 分隔符间距 - 16.0
  static const double dividerSpace = 16.0;

  // 间隔组件尺寸
  /// 极小垂直间隔 - SizedBox(height: 4.0)
  static const SizedBox verticalSpacerXs = SizedBox(height: spacingXs);

  /// 小垂直间隔 - SizedBox(height: 8.0)
  static const SizedBox verticalSpacerS = SizedBox(height: spacingS);

  /// 中等垂直间隔 - SizedBox(height: 16.0)
  static const SizedBox verticalSpacerM = SizedBox(height: spacingM);

  /// 大垂直间隔 - SizedBox(height: 24.0)
  static const SizedBox verticalSpacerL = SizedBox(height: spacingL);

  /// 特大垂直间隔 - SizedBox(height: 32.0)
  static const SizedBox verticalSpacerXl = SizedBox(height: spacingXl);

  /// 极小水平间隔 - SizedBox(width: 4.0)
  static const SizedBox horizontalSpacerXs = SizedBox(width: spacingXs);

  /// 小水平间隔 - SizedBox(width: 8.0)
  static const SizedBox horizontalSpacerS = SizedBox(width: spacingS);

  /// 中等水平间隔 - SizedBox(width: 16.0)
  static const SizedBox horizontalSpacerM = SizedBox(width: spacingM);

  /// 大水平间隔 - SizedBox(width: 24.0)
  static const SizedBox horizontalSpacerL = SizedBox(width: spacingL);

  /// 特大水平间隔 - SizedBox(width: 32.0)
  static const SizedBox horizontalSpacerXl = SizedBox(width: spacingXl);

  // 设备尺寸断点
  /// 手机宽度上限 - 600.0
  static const double breakpointPhone = 600.0;

  /// 平板宽度上限 - 900.0
  static const double breakpointTablet = 900.0;

  /// 获取屏幕宽度
  static double screenWidth(BuildContext context) => MediaQuery.of(context).size.width;

  /// 获取屏幕高度
  static double screenHeight(BuildContext context) => MediaQuery.of(context).size.height;

  /// 判断是否为手机尺寸
  static bool isPhone(BuildContext context) => screenWidth(context) < breakpointPhone;

  /// 判断是否为平板尺寸
  static bool isTablet(BuildContext context) =>
      screenWidth(context) >= breakpointPhone && screenWidth(context) < breakpointTablet;

  /// 判断是否为桌面尺寸
  static bool isDesktop(BuildContext context) => screenWidth(context) >= breakpointTablet;
}
