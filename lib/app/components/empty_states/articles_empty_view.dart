import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';

/// 文章列表空状态组件
///
/// 当文章列表为空时显示的提示组件，包含：
/// - 圆形图标背景
/// - 文章图标
/// - 主标题和副标题
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
    return Container(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          _buildIconContainer(context),
          const SizedBox(height: 24),
          _buildTitle(context),
          const SizedBox(height: 12),
          _buildSubtitle(context),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  /// 构建图标容器
  Widget _buildIconContainer(BuildContext context) {
    return Container(
      width: 120,
      height: 120,
      decoration: BoxDecoration(color: AppColors.primary(context).withAlpha(26), shape: BoxShape.circle),
      child: Icon(Icons.article_outlined, size: 60, color: AppColors.primary(context)),
    );
  }

  /// 构建主标题
  Widget _buildTitle(BuildContext context) {
    return Text(
      '还没有收藏内容',
      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppColors.textPrimary(context)),
    );
  }

  /// 构建副标题
  Widget _buildSubtitle(BuildContext context) {
    return Text('您可以通过分享功能添加新文章', style: TextStyle(fontSize: 14, color: AppColors.textSecondary(context)));
  }
}
