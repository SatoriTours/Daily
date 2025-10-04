import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 通用空状态提示组件
///
/// 一个可定制的空状态提示组件，支持以下特性：
/// - 自定义图标
/// - 主标题和副标题
/// - 可选的操作按钮
/// - 自动适应主题样式
///
/// 使用示例:
/// ```dart
/// EmptyStateWidget(
///   icon: Icons.inbox,
///   title: '暂无数据',
///   subtitle: '点击下方按钮添加新内容',
///   action: ElevatedButton(
///     onPressed: () {},
///     child: Text('添加'),
///   ),
/// )
/// ```
class EmptyStateWidget extends StatelessWidget {
  /// 空状态图标
  final IconData icon;

  /// 主标题文本
  final String title;

  /// 副标题文本
  ///
  /// 可选，如果为 null 则不显示
  final String? subtitle;

  /// 操作按钮
  ///
  /// 可选，如果为 null 则不显示
  final Widget? action;

  /// 创建一个空状态提示组件
  ///
  /// [icon] 显示的图标
  /// [title] 主标题文本
  /// [subtitle] 可选的副标题文本
  /// [action] 可选的操作按钮
  const EmptyStateWidget({super.key, required this.icon, required this.title, this.subtitle, this.action});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _buildIcon(context),
            const SizedBox(height: 16),
            _buildTitle(context),
            if (subtitle != null) _buildSubtitle(context),
            if (action != null) _buildAction(),
          ],
        ),
      ),
    );
  }

  /// 构建图标
  Widget _buildIcon(BuildContext context) {
    return Icon(icon, size: 64, color: AppColors.textSecondary(context).withAlpha(128));
  }

  /// 构建主标题
  Widget _buildTitle(BuildContext context) {
    return Text(title, style: MyFontStyle.emptyStateStyleThemed(context), textAlign: TextAlign.center);
  }

  /// 构建副标题
  Widget _buildSubtitle(BuildContext context) {
    return Column(
      children: [
        const SizedBox(height: 8),
        Text(subtitle!, style: MyFontStyle.cardSubtitleStyleThemed(context), textAlign: TextAlign.center),
      ],
    );
  }

  /// 构建操作按钮
  Widget _buildAction() {
    return Column(children: [const SizedBox(height: 24), action!]);
  }
}
