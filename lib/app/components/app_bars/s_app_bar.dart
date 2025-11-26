import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 通用可复用 AppBar
///
/// 提供：
/// - 深浅色下不同背景色
/// - 标题双击回到顶部（可选）
/// - 自定义 leading / actions / 居中 / 阴影
class SAppBar extends StatelessWidget implements PreferredSizeWidget {
  const SAppBar({
    super.key,
    required this.title,
    this.onTitleDoubleTap,
    this.leading,
    this.actions,
    this.centerTitle = true,
    this.elevation = 1,
    this.backgroundColorLight,
    this.backgroundColorDark,
    this.foregroundColor,
    this.toolbarHeight = kToolbarHeight,
  });

  /// 标题（文本/自定义小部件）
  final Widget title;

  /// 标题双击回调（例如：滚动到顶部）
  final VoidCallback? onTitleDoubleTap;

  /// 左侧按钮
  final Widget? leading;

  /// 右侧按钮组
  final List<Widget>? actions;

  /// 标题是否居中
  final bool centerTitle;

  /// 阴影
  final double elevation;

  /// 浅色主题下背景色
  final Color? backgroundColorLight;

  /// 深色主题下背景色
  final Color? backgroundColorDark;

  /// 前景色（影响标题与图标颜色）
  final Color? foregroundColor;

  /// 工具栏高度
  final double toolbarHeight;

  @override
  Size get preferredSize => Size.fromHeight(toolbarHeight);

  @override
  Widget build(BuildContext context) {
    final bg = backgroundColorLight ?? backgroundColorDark ?? AppColors.getSurface(context);

    final titleWidget = onTitleDoubleTap == null ? title : GestureDetector(onDoubleTap: onTitleDoubleTap, child: title);

    return AppBar(
      backgroundColor: bg,
      elevation: elevation,
      centerTitle: centerTitle,
      leading: leading,
      title: titleWidget,
      actions: actions,
      foregroundColor: foregroundColor,
      toolbarHeight: toolbarHeight,
    );
  }
}
