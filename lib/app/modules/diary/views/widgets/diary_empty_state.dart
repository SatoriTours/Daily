import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 日记空状态组件
class DiaryEmptyState extends StatelessWidget {
  final String message;

  const DiaryEmptyState({super.key, this.message = '还没有日记，开始记录今天的想法吧'});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            FeatherIcons.book,
            size: 64,
            color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[700] : Colors.grey[300],
          ),
          const SizedBox(height: 16),
          Text(message, style: TextStyle(fontSize: 16, color: DiaryStyle.secondaryTextColor(context))),
        ],
      ),
    );
  }
}
