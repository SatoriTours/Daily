import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/app_theme.dart';

import '../../controllers/articles_controller.dart';

/// 文章搜索栏组件
class ArticlesSearchBar extends StatelessWidget {
  final ArticlesController controller;
  final VoidCallback onClose;

  const ArticlesSearchBar({Key? key, required this.controller, required this.onClose}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final isDark = AppTheme.isDarkMode(context);
    final textColor = isDark ? Colors.white : Colors.black87;
    final secondaryTextColor = isDark ? Colors.white70 : Colors.black54;

    // 当搜索栏出现时自动聚焦
    WidgetsBinding.instance.addPostFrameCallback((_) {
      FocusScope.of(context).requestFocus(FocusNode());
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
            icon: Icon(FeatherIcons.arrowLeft, color: textColor, size: 20),
            onPressed: onClose,
            splashRadius: 20,
          ),
          Expanded(
            child: TextField(
              controller: controller.searchController,
              decoration: InputDecoration(
                hintText: '搜索文章...',
                hintStyle: TextStyle(color: secondaryTextColor),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                isDense: true,
              ),
              style: TextStyle(color: textColor),
              textInputAction: TextInputAction.search,
              onSubmitted: (value) {
                controller.searchArticles();
                if (value.trim().isEmpty) {
                  onClose();
                }
              },
            ),
          ),
          IconButton(
            icon: Icon(FeatherIcons.search, color: textColor, size: 20),
            onPressed: () {
              controller.searchArticles();
              if (controller.searchController.text.trim().isEmpty) {
                onClose();
              }
            },
            splashRadius: 20,
          ),
          if (controller.searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(FeatherIcons.x, color: textColor, size: 20),
              onPressed: () {
                controller.searchController.clear();
                controller.clearAllFilters();
                onClose();
              },
              splashRadius: 20,
            ),
        ],
      ),
    );
  }
}
