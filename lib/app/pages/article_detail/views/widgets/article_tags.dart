import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/styles.dart';

/// 文章标签组件
///
/// 显示文章的标签列表，支持：
/// - 美观的标签样式
/// - 自动换行
/// - 响应式布局
class ArticleTags extends StatelessWidget {
  final String tags;

  const ArticleTags({super.key, required this.tags});

  @override
  Widget build(BuildContext context) {
    if (tags.isEmpty) return const SizedBox.shrink();

    final tagList = tags
        .split(', ')
        .where((tag) => tag.trim().isNotEmpty)
        .toList();

    if (tagList.isEmpty) return const SizedBox.shrink();

    return Wrap(
      spacing: Dimensions.spacingS,
      runSpacing: Dimensions.spacingS,
      children: tagList.map((tag) => _buildTag(context, tag)).toList(),
    );
  }

  /// 构建单个标签
  Widget _buildTag(BuildContext context, String tag) {
    final accentColor = DiaryStyles.getAccentColor(context);
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: Dimensions.spacingS + 2,
        vertical: Dimensions.spacingXs,
      ),
      decoration: BoxDecoration(
        color: accentColor.withAlpha(20),
        borderRadius: Dimensions.borderRadiusM,
        border: Border.all(color: accentColor.withAlpha(50), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.tag, size: 14, color: accentColor),
          const SizedBox(width: Dimensions.spacingXs),
          Text(
            tag,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w500,
              color: accentColor,
            ),
          ),
        ],
      ),
    );
  }
}
