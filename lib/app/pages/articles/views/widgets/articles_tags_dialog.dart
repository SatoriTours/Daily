import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/data/tag/tag_model.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

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
          padding: Dimensions.paddingM,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('article.select_tag'.t, style: textTheme.titleMedium),
              IconButton(
                icon: Icon(FeatherIcons.x, size: Dimensions.iconSizeM),
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
          padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingS, horizontal: Dimensions.spacingM - 4),
          child: Wrap(
            spacing: Dimensions.spacingS,
            runSpacing: Dimensions.spacingS,
            children: tags.map((tag) => _buildTagItem(context, tag)).toList(),
          ),
        ),

        Divider(height: 1, color: colorScheme.outline.withAlpha(128)),

        // 清除过滤按钮
        Padding(
          padding: Dimensions.paddingM,
          child: Center(
            child: InkWell(
              onTap: () {
                onClearFilters();
                Navigator.pop(context);
              },
              borderRadius: BorderRadius.circular(Dimensions.radiusL + 4),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(FeatherIcons.x, size: Dimensions.iconSizeXs, color: colorScheme.primary),
                    Dimensions.horizontalSpacerS,
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
      borderRadius: BorderRadius.circular(Dimensions.radiusL),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM - 4, vertical: Dimensions.spacingXs + 2),
        decoration: BoxDecoration(color: backgroundColor, borderRadius: BorderRadius.circular(Dimensions.radiusL)),
        child: Text(tag.name ?? '', style: TextStyle(fontSize: 13, color: textColor)),
      ),
    );
  }
}
