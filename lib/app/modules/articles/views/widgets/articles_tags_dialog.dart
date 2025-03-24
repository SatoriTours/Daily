import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/app/models/tag_model.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';

import '../../controllers/articles_controller.dart';

/// 文章标签对话框
class ArticlesTagsDialog extends GetView<ArticlesController> {
  const ArticlesTagsDialog({super.key});

  @override
  Widget build(BuildContext context) {
    final isDark = AppTheme.isDarkMode(context);
    final textTheme = AppTheme.getTextTheme(context);
    final colorScheme = AppTheme.getColorScheme(context);

    // 获取所有标签
    final tags = TagRepository.all();

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

        Divider(height: 1, color: colorScheme.outline.withOpacity(0.5)),

        // 标签列表
        SingleChildScrollView(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
          child: Wrap(spacing: 8, runSpacing: 8, children: tags.map((tag) => _buildTagItem(context, tag)).toList()),
        ),

        Divider(height: 1, color: colorScheme.outline.withOpacity(0.5)),

        // 清除过滤按钮
        Padding(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: InkWell(
              onTap: () {
                controller.clearAllFilters();
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
    final isSelected = controller.tagId.value == tag.id;

    final backgroundColor = isSelected ? colorScheme.primary.withOpacity(0.2) : colorScheme.surfaceContainerHighest;

    final textColor = isSelected ? colorScheme.primary : colorScheme.onSurface;

    return InkWell(
      onTap: () {
        controller.filterByTag(tag.id, tag.name ?? '');
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
