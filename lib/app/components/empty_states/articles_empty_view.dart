import 'package:flutter/material.dart';
import 'empty_state_widget.dart';

/// 文章列表空状态组件
///
/// 当文章列表为空时显示的专用提示组件，包含：
/// - 圆形图标背景
/// - 文章图标
/// - 国际化的主标题和副标题
/// - 统一的视觉样式
///
/// 使用示例:
/// ```dart
/// ArticlesEmptyView()
/// ```
class ArticlesEmptyView extends StatelessWidget {
  /// 创建一个文章列表空状态组件
  const ArticlesEmptyView({super.key});

  @override
  Widget build(BuildContext context) {
    return const EmptyStateWidget(
      icon: Icons.article_outlined,
      titleKey: 'component.empty_articles_title',
      subtitleKey: 'component.empty_articles_subtitle',
      compact: false,
    );
  }
}