import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/diary_controller_provider.dart';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';

import 'diary_tags_dialog_header.dart';
import 'diary_tags_list.dart';
import 'diary_clear_filters_button.dart';

/// 日记标签对话框
class DiaryTagsDialog extends ConsumerWidget {
  const DiaryTagsDialog({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controllerState = ref.watch(diaryControllerProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        DiaryTagsDialogHeader(onClose: () => Navigator.pop(context)),
        Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
        DiaryTagsList(
          tags: controllerState.tags,
          onTagSelected: (tag) {
            ref.read(diaryControllerProvider.notifier).filterByTag(tag);
            Navigator.pop(context);
          },
        ),
        Divider(height: 1, thickness: 0.5, color: DiaryStyles.getDividerColor(context)),
        DiaryClearFiltersButton(
          onPressed: () {
            ref.read(diaryControllerProvider.notifier).clearFilters();
            Navigator.pop(context);
          },
        ),
        const SizedBox(height: 8),
      ],
    );
  }
}
