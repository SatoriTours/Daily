import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import '../../utils/diary_utils.dart';

/// 日记日期标题组件
class DiaryDateHeader extends StatelessWidget {
  final DateTime date;

  const DiaryDateHeader({super.key, required this.date});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 8),
      margin: const EdgeInsets.only(bottom: 4),
      decoration: BoxDecoration(
        color: DiaryStyle.tagBackgroundColor(context).withAlpha(128),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        DiaryUtils.formatDate(date),
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: DiaryStyle.secondaryTextColor(context),
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}
