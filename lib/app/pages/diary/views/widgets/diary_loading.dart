import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 日记加载状态组件
class DiaryLoading extends StatelessWidget {
  final String? message;

  const DiaryLoading({super.key, this.message = '正在加载日记...'});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(color: DiaryStyles.getAccentColor(context)),
          if (message != null) ...[
            Dimensions.verticalSpacerM,
            Text(
              message!,
              style: TextStyle(
                fontSize: 14,
                color: DiaryStyles.getSecondaryTextColor(context),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
