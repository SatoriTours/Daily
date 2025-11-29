import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';

import '../../controllers/diary_controller.dart';
import 'diary_tags_dialog_header.dart';
import 'diary_tags_list.dart';
import 'diary_clear_filters_button.dart';

/// 日记标签对话框
class DiaryTagsDialog extends StatelessWidget {
  final DiaryController controller;

  const DiaryTagsDialog({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        DiaryTagsDialogHeader(onClose: () => Navigator.pop(context)),
        Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
        DiaryTagsList(
          tags: controller.tags,
          onTagSelected: (tag) {
            controller.filterByTag(tag);
            Navigator.pop(context);
          },
        ),
        Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
        DiaryClearFiltersButton(
          onPressed: () {
            controller.clearFilters();
            Navigator.pop(context);
          },
        ),
        const SizedBox(height: 8),
      ],
    );
  }
}
