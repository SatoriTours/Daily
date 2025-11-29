import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/index.dart';

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
        padding: EdgeInsets.symmetric(horizontal: Dimensions.spacingXxl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 100,
              height: 100,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: isDark
                    ? DiaryStyles.getAccentColor(context).withAlpha(20)
                    : DiaryStyles.getAccentColor(context).withAlpha(12),
              ),
              child: Icon(icon, size: 40, color: DiaryStyles.getAccentColor(context)),
            ),
            Dimensions.verticalSpacerL,
            Text(
              message,
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w500, color: DiaryStyles.getPrimaryTextColor(context)),
              textAlign: TextAlign.center,
            ),
            if (subMessage != null) ...[
              Dimensions.verticalSpacerS,
              Text(
                subMessage!,
                style: TextStyle(fontSize: 14, color: DiaryStyles.getSecondaryTextColor(context)),
                textAlign: TextAlign.center,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
