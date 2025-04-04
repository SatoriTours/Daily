import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:get/get.dart';

import '../../controllers/diary_controller.dart';

/// 日记搜索栏组件
class DiarySearchBar extends StatelessWidget {
  final DiaryController controller;
  final VoidCallback onClose;

  const DiarySearchBar({super.key, required this.controller, required this.onClose});

  @override
  Widget build(BuildContext context) {
    // 当搜索栏出现时自动聚焦
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // 使用控制器中的焦点节点
      FocusScope.of(context).requestFocus(controller.searchFocusNode);
      controller.searchController.selection = TextSelection.fromPosition(
        TextPosition(offset: controller.searchController.text.length),
      );
    });

    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: DiaryStyle.cardColor(context),
        boxShadow: [BoxShadow(color: Colors.black.withAlpha(10), offset: const Offset(0, 1), blurRadius: 3)],
      ),
      child: Row(
        children: [
          IconButton(
            icon: Icon(FeatherIcons.arrowLeft, color: DiaryStyle.primaryTextColor(context), size: 20),
            onPressed: onClose,
            splashRadius: 20,
          ),
          Expanded(
            child: TextField(
              controller: controller.searchController,
              focusNode: controller.searchFocusNode, // 使用控制器中的焦点节点
              decoration: InputDecoration(
                hintText: '搜索日记...',
                hintStyle: TextStyle(color: DiaryStyle.secondaryTextColor(context)),
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                isDense: true,
              ),
              style: TextStyle(color: DiaryStyle.primaryTextColor(context)),
              textInputAction: TextInputAction.search,
              onSubmitted: (value) {
                controller.search(value);
                if (value.trim().isEmpty) {
                  onClose();
                }
              },
            ),
          ),
          IconButton(
            icon: Icon(FeatherIcons.search, color: DiaryStyle.primaryTextColor(context), size: 20),
            onPressed: () {
              controller.search(controller.searchController.text);
              if (controller.searchController.text.trim().isEmpty) {
                onClose();
              }
            },
            splashRadius: 20,
          ),
          if (controller.searchController.text.isNotEmpty)
            IconButton(
              icon: Icon(FeatherIcons.x, color: DiaryStyle.primaryTextColor(context), size: 20),
              onPressed: () {
                controller.searchController.clear();
                controller.clearFilters();
                onClose();
              },
              splashRadius: 20,
            ),
        ],
      ),
    );
  }
}
