import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 日记空状态组件
class DiaryEmptyState extends StatelessWidget {
  final String message;
  final String? subMessage;
  final IconData icon;

  const DiaryEmptyState({
    super.key,
    this.message = '还没有日记',
    this.subMessage = '开始记录今天的想法吧',
    this.icon = FeatherIcons.book,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 100,
              height: 100,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color:
                    isDark
                        ? DiaryStyle.accentColor(context).withAlpha(20)
                        : DiaryStyle.accentColor(context).withAlpha(12),
              ),
              child: Icon(icon, size: 40, color: DiaryStyle.accentColor(context)),
            ),
            const SizedBox(height: 24),
            Text(
              message,
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500, color: DiaryStyle.primaryTextColor(context)),
              textAlign: TextAlign.center,
            ),
            if (subMessage != null) ...[
              const SizedBox(height: 8),
              Text(
                subMessage!,
                style: TextStyle(fontSize: 14, color: DiaryStyle.secondaryTextColor(context)),
                textAlign: TextAlign.center,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
