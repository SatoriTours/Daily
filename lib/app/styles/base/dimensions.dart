import 'package:flutter/material.dart';

/// 应用尺寸常量类
/// 统一管理应用中的尺寸、边距、圆角等样式，遵循 shadcn 的设计风格
class Dimensions {
  // 私有构造函数，防止实例化
  Dimensions._();

  // 边距常量
  /// 极小边距 - 4.0
  static const double spacingXxs = 2.0;
  static const double spacingXs = 4.0;
  static const double spacingS = 8.0;
  static const double spacingM = 16.0;
  static const double spacingL = 24.0;
  static const double spacingXl = 32.0;
  static const double spacingXxl = 48.0;

  // 内边距预设
  /// 无内边距
  static const EdgeInsets paddingNone = EdgeInsets.zero;

  /// 极小内边距 (2)
  static const EdgeInsets paddingXxs = EdgeInsets.all(spacingXxs);

  /// 极小内边距 (4)
  static const EdgeInsets paddingXs = EdgeInsets.all(spacingXs);

  /// 小内边距 (8)
  static const EdgeInsets paddingS = EdgeInsets.all(spacingS);

  /// 中等内边距 (16)
  static const EdgeInsets paddingM = EdgeInsets.all(spacingM);

  /// 大内边距 (24)
  static const EdgeInsets paddingL = EdgeInsets.all(spacingL);

  /// 特大内边距 (32)
  static const EdgeInsets paddingXl = EdgeInsets.all(spacingXl);

  /// 页面内边距 (20, 16)
  static const EdgeInsets paddingPage = EdgeInsets.symmetric(horizontal: 20, vertical: spacingM);

  // 水平内边距预设
  /// 极小水平内边距 (4, 0)
  static const EdgeInsets paddingHorizontalXs = EdgeInsets.symmetric(horizontal: spacingXs);

  /// 小水平内边距 (8, 0)
  static const EdgeInsets paddingHorizontalS = EdgeInsets.symmetric(horizontal: spacingS);

  /// 中等水平内边距 (16, 0)
  static const EdgeInsets paddingHorizontalM = EdgeInsets.symmetric(horizontal: spacingM);

  /// 大水平内边距 (24, 0)
  static const EdgeInsets paddingHorizontalL = EdgeInsets.symmetric(horizontal: spacingL);

  /// 特大水平内边距 (32, 0)
  static const EdgeInsets paddingHorizontalXl = EdgeInsets.symmetric(horizontal: spacingXl);

  // 垂直内边距预设
  /// 极小垂直内边距 (0, 4)
  static const EdgeInsets paddingVerticalXs = EdgeInsets.symmetric(vertical: spacingXs);

  /// 小垂直内边距 (0, 8)
  static const EdgeInsets paddingVerticalS = EdgeInsets.symmetric(vertical: spacingS);

  /// 中等垂直内边距 (0, 16)
  static const EdgeInsets paddingVerticalM = EdgeInsets.symmetric(vertical: spacingM);

  /// 大垂直内边距 (0, 24)
  static const EdgeInsets paddingVerticalL = EdgeInsets.symmetric(vertical: spacingL);

  /// 特大垂直内边距 (0, 32)
  static const EdgeInsets paddingVerticalXl = EdgeInsets.symmetric(vertical: spacingXl);

  // 常用组件内边距预设
  /// 卡片内边距 (16)
  static const EdgeInsets paddingCard = EdgeInsets.all(spacingM);

  /// 对话框内边距 (24)
  static const EdgeInsets paddingDialog = EdgeInsets.all(spacingL);

  /// 列表项内边距 (16, 12)
  static const EdgeInsets paddingListItem = EdgeInsets.symmetric(horizontal: spacingM, vertical: 12);

  /// 表单项内边距 (16, 16)
  static const EdgeInsets paddingFormItem = EdgeInsets.all(spacingM);

  /// 按钮内边距 (16, 12)
  static const EdgeInsets paddingButton = EdgeInsets.symmetric(horizontal: spacingM, vertical: 12);

  /// 小按钮内边距 (12, 8)
  static const EdgeInsets paddingButtonSmall = EdgeInsets.symmetric(horizontal: 12, vertical: 8);

  /// 图标按钮内边距 (8)
  static const EdgeInsets paddingIconButton = EdgeInsets.all(spacingS);

  /// 搜索框内边距 (16, 0)
  static const EdgeInsets paddingSearchBar = EdgeInsets.symmetric(horizontal: spacingM);

  /// 输入框内边距 (16, 12)
  static const EdgeInsets paddingInput = EdgeInsets.symmetric(horizontal: spacingM, vertical: 12);

  /// 底部表单内边距 (16, 16, 16, 32)
  static const EdgeInsets paddingBottomForm = EdgeInsets.fromLTRB(spacingM, spacingM, spacingM, spacingXl);

  // 外边距预设
  /// 卡片外边距 (0, 8)
  static const EdgeInsets marginCard = EdgeInsets.symmetric(vertical: spacingS);

  /// 列表项外边距 (0, 4)
  static const EdgeInsets marginListItem = EdgeInsets.symmetric(vertical: spacingXs);

  /// 表单项外边距 (0, 12)
  static const EdgeInsets marginFormItem = EdgeInsets.symmetric(vertical: 12);

  /// 段落外边距 (0, 8)
  static const EdgeInsets marginParagraph = EdgeInsets.symmetric(vertical: spacingS);

  /// 页面内容外边距 (16, 16)
  static const EdgeInsets marginPage = EdgeInsets.all(spacingM);

  /// 底部按钮外边距 (16, 16, 16, 24)
  static const EdgeInsets marginBottomButton = EdgeInsets.fromLTRB(spacingM, spacingM, spacingM, spacingL);

  // 圆角常量
  /// 无圆角
  static const double radiusNone = 0.0;

  /// 极小圆角 - 2.0
  static const double radiusXxs = 2.0;

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

  /// 圆形圆角 - 100.0（用于圆形UI元素）
  static const double radiusCircular = 100.0;

  // 圆角预设
  /// 小圆角边框 (4.0)
  static final BorderRadius borderRadiusXs = BorderRadius.circular(radiusXs);

  /// 中圆角边框 (8.0)
  static final BorderRadius borderRadiusS = BorderRadius.circular(radiusS);

  /// 大圆角边框 (12.0)
  static final BorderRadius borderRadiusM = BorderRadius.circular(radiusM);

  /// 超大圆角边框 (16.0)
  static final BorderRadius borderRadiusL = BorderRadius.circular(radiusL);

  /// 上方圆角 (16.0)
  static const BorderRadius borderRadiusTop = BorderRadius.vertical(top: Radius.circular(radiusL));

  /// 下方圆角 (16.0)
  static const BorderRadius borderRadiusBottom = BorderRadius.vertical(bottom: Radius.circular(radiusL));

  // UI元素高度
  /// 标准按钮高度 - 48.0
  static const double buttonHeight = 48.0;

  /// 小按钮高度 - 36.0
  static const double buttonHeightSmall = 36.0;

  /// 输入框高度 - 48.0
  static const double inputHeight = 48.0;

  /// 列表项标准高度 - 56.0
  static const double listItemHeight = 56.0;

  /// 列表项小高度 - 48.0
  static const double listItemHeightSmall = 48.0;

  /// 应用栏高度 - 56.0
  static const double appBarHeight = 56.0;

  /// 底部导航栏高度 - 56.0
  static const double navBarHeight = 56.0;

  /// 标签高度 - 32.0
  static const double chipHeight = 32.0;

  /// 搜索栏高度 - 48.0
  static const double searchBarHeight = 48.0;

  /// 下拉菜单项高度 - 40.0
  static const double dropdownItemHeight = 40.0;

  // 图标尺寸
  /// 极小图标尺寸 - 16.0
  static const double iconSizeXs = 16.0;

  /// 小图标尺寸 - 18.0
  static const double iconSizeS = 18.0;

  /// 中等图标尺寸 - 20.0
  static const double iconSizeM = 20.0;

  /// 标准图标尺寸 - 24.0
  static const double iconSizeL = 24.0;

  /// 大图标尺寸 - 32.0
  static const double iconSizeXl = 32.0;

  /// 特大图标尺寸 - 48.0
  static const double iconSizeXxl = 48.0;

  // 分隔线相关
  /// 分隔线高度 - 1.0
  static const double dividerHeight = 1.0;

  /// 分隔线水平内边距 - 16.0
  static const double dividerIndent = 16.0;

  // 边框宽度常量
  /// 极细边框宽度 - 0.5
  static const double borderWidthXs = 0.5;

  /// 细边框宽度 - 1.0
  static const double borderWidthS = 1.0;

  /// 中等边框宽度 - 1.5
  static const double borderWidthM = 1.5;

  /// 粗边框宽度 - 2.0
  static const double borderWidthL = 2.0;

  /// 极粗边框宽度 - 4.0
  static const double borderWidthXl = 4.0;

  // 间隔组件预设（垂直）
  /// 极小垂直间隔 - 4.0
  static const SizedBox verticalSpacerXs = SizedBox(height: spacingXs);

  /// 小垂直间隔 - 8.0
  static const SizedBox verticalSpacerS = SizedBox(height: spacingS);

  /// 中等垂直间隔 - 16.0
  static const SizedBox verticalSpacerM = SizedBox(height: spacingM);

  /// 大垂直间隔 - 24.0
  static const SizedBox verticalSpacerL = SizedBox(height: spacingL);

  /// 特大垂直间隔 - 32.0
  static const SizedBox verticalSpacerXl = SizedBox(height: spacingXl);

  // 间隔组件预设（水平）
  /// 极小水平间隔 - 4.0
  static const SizedBox horizontalSpacerXs = SizedBox(width: spacingXs);

  /// 小水平间隔 - 8.0
  static const SizedBox horizontalSpacerS = SizedBox(width: spacingS);

  /// 中等水平间隔 - 16.0
  static const SizedBox horizontalSpacerM = SizedBox(width: spacingM);

  /// 大水平间隔 - 24.0
  static const SizedBox horizontalSpacerL = SizedBox(width: spacingL);

  /// 特大水平间隔 - 32.0
  static const SizedBox horizontalSpacerXl = SizedBox(width: spacingXl);

  // 响应式布局断点
  /// 手机宽度上限 - 600.0
  static const double breakpointMobile = 600.0;

  /// 平板宽度上限 - 900.0
  static const double breakpointTablet = 900.0;

  /// 桌面宽度上限 - 1200.0
  static const double breakpointDesktop = 1200.0;

  /// 获取屏幕宽度
  static double getScreenWidth(BuildContext context) => MediaQuery.of(context).size.width;

  /// 获取屏幕高度
  static double getScreenHeight(BuildContext context) => MediaQuery.of(context).size.height;

  /// 获取状态栏高度
  static double getStatusBarHeight(BuildContext context) => MediaQuery.of(context).padding.top;

  /// 获取底部安全区域高度
  static double getBottomSafeAreaHeight(BuildContext context) => MediaQuery.of(context).padding.bottom;

  /// 判断是否为手机屏幕
  static bool isMobile(BuildContext context) => getScreenWidth(context) < breakpointMobile;

  /// 判断是否为平板屏幕
  static bool isTablet(BuildContext context) =>
      getScreenWidth(context) >= breakpointMobile && getScreenWidth(context) < breakpointTablet;

  /// 判断是否为桌面屏幕
  static bool isDesktop(BuildContext context) => getScreenWidth(context) >= breakpointTablet;
}
