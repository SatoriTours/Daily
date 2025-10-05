import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/models/tag_model.dart';

/// 文章标签对话框
///
/// 纯展示组件,通过回调函数与外部交互
class ArticlesTagsDialog extends StatelessWidget {
  final List<TagModel> tags;
  final int? selectedTagId;
  final void Function(int tagId, String tagName) onTagSelected;
  final VoidCallback onClearFilters;

  const ArticlesTagsDialog({
    super.key,
    required this.tags,
    this.selectedTagId,
    required this.onTagSelected,
    required this.onClearFilters,
  });

  @override
  Widget build(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        // 标题栏
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('选择标签', style: textTheme.titleMedium),
              IconButton(
                icon: const Icon(FeatherIcons.x, size: 20),
                onPressed: () => Navigator.pop(context),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
              ),
            ],
          ),
        ),

        Divider(height: 1, color: colorScheme.outline.withAlpha(128)),

        // 标签列表
        SingleChildScrollView(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
          child: Wrap(spacing: 8, runSpacing: 8, children: tags.map((tag) => _buildTagItem(context, tag)).toList()),
        ),

        Divider(height: 1, color: colorScheme.outline.withAlpha(128)),

        // 清除过滤按钮
        Padding(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: InkWell(
              onTap: () {
                onClearFilters();
                Navigator.pop(context);
              },
              borderRadius: BorderRadius.circular(20),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(FeatherIcons.x, size: 16, color: colorScheme.primary),
                    const SizedBox(width: 8),
                    Text('清除所有过滤', style: TextStyle(color: colorScheme.primary)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  /// 构建单个标签项
  Widget _buildTagItem(BuildContext context, TagModel tag) {
    final colorScheme = AppTheme.getColorScheme(context);
    final isSelected = selectedTagId == tag.id;

    final backgroundColor = isSelected ? colorScheme.primary.withAlpha(51) : colorScheme.surfaceContainerHighest;

    final textColor = isSelected ? colorScheme.primary : colorScheme.onSurface;

    return InkWell(
      onTap: () {
        onTagSelected(tag.id, tag.name ?? '');
        Navigator.pop(context);
      },
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(color: backgroundColor, borderRadius: BorderRadius.circular(16)),
        child: Text(tag.name ?? '', style: TextStyle(fontSize: 13, color: textColor)),
      ),
    );
  }
}
