import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';

/// 日记时间戳组件
class DiaryTimestamp extends StatelessWidget {
  final DateTime timestamp;

  const DiaryTimestamp({super.key, required this.timestamp});

  @override
  Widget build(BuildContext context) {
    return Text(
      _formatDateTime(timestamp),
      style: TextStyle(fontSize: 11, color: DiaryStyles.getSecondaryTextColor(context)),
    );
  }

  /// 格式化日期和时间为 "yyyy-MM-dd HH:mm:ss" 格式
  String _formatDateTime(DateTime dateTime) {
    return DateFormat('yyyy-MM-dd HH:mm:ss').format(dateTime);
  }
}
