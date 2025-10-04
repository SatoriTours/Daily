import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

import '../../controllers/articles_controller.dart';

/// 文章搜索栏组件
class ArticlesSearchBar extends GetView<ArticlesController> {
  const ArticlesSearchBar({super.key});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final textTheme = AppTheme.getTextTheme(context);

    // 当搜索栏出现时自动聚焦
    WidgetsBinding.instance.addPostFrameCallback((_) {
      FocusScope.of(context).requestFocus(controller.searchFocusNode);
      controller.searchController.selection = TextSelection.fromPosition(
        TextPosition(offset: controller.searchController.text.length),
      );
    });

    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(10), offset: const Offset(0, 1), blurRadius: 3)],
      ),
      child: Row(
        children: [
          IconButton(
            icon: Icon(FeatherIcons.arrowLeft, color: colorScheme.onSurface, size: 20),
            onPressed: controller.toggleSearchState,
            splashRadius: 20,
          ),
          Expanded(
            child: TextField(
              controller: controller.searchController,
              focusNode: controller.searchFocusNode,
              decoration: InputDecoration(
                hintText: '搜索文章...',
                hintStyle: textTheme.bodyMedium?.copyWith(color: colorScheme.onSurfaceVariant),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                isDense: true,
              ),
              style: textTheme.bodyMedium,
              textInputAction: TextInputAction.search,
              onSubmitted: (value) {
                controller.searchArticles();
              },
            ),
          ),
          IconButton(
            icon: Icon(FeatherIcons.search, color: colorScheme.onSurface, size: 20),
            onPressed: controller.searchArticles,
            splashRadius: 20,
          ),
          if (controller.searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(FeatherIcons.x, color: colorScheme.onSurface, size: 20),
              onPressed: () {
                controller.searchController.clear();
                controller.clearAllFilters();
              },
              splashRadius: 20,
            ),
        ],
      ),
    );
  }
}
