import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 自定义加载指示器
class LoadingIndicator extends StatelessWidget {
  final String? message;

  const LoadingIndicator({super.key, this.message});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(valueColor: AlwaysStoppedAnimation<Color>(AppColors.primary(context))),
          if (message != null) ...[
            const SizedBox(height: 16),
            Text(message!, style: MyFontStyle.loadingTipsStyleThemed(context)),
          ],
        ],
      ),
    );
  }
}
