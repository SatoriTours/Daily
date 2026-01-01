import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';

import 'diary_tags_dialog_header.dart';
import 'diary_tags_list.dart';
import 'diary_clear_filters_button.dart';

/// 日记标签对话框
class DiaryTagsDialog extends StatelessWidget {
  final List<String> tags;
  final ValueChanged<String> onTagSelected;
  final VoidCallback onClearFilters;

  const DiaryTagsDialog({super.key, required this.tags, required this.onTagSelected, required this.onClearFilters});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        DiaryTagsDialogHeader(onClose: () => Navigator.pop(context)),
        Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
        DiaryTagsList(tags: tags, onTagSelected: onTagSelected),
        Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
        DiaryClearFiltersButton(onPressed: onClearFilters),
        const SizedBox(height: 8),
      ],
    );
  }
}
