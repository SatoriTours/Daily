import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';

/// 日记标签对话框标题
class DiaryTagsDialogHeader extends StatelessWidget {
  final String title;
  final VoidCallback onClose;

  const DiaryTagsDialogHeader({super.key, this.title = '选择标签', required this.onClose});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            title,
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: DiaryStyles.getPrimaryTextColor(context)),
          ),
          IconButton(
            icon: Icon(FeatherIcons.x, size: 20, color: DiaryStyles.getSecondaryTextColor(context)),
            onPressed: onClose,
          ),
        ],
      ),
    );
  }
}
