import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 自定义按钮
class CustomButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final bool isPrimary;
  final bool isFullWidth;

  const CustomButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
    this.isPrimary = true,
    this.isFullWidth = false,
  });

  @override
  Widget build(BuildContext context) {
    final buttonStyle =
        isPrimary
            ? ElevatedButton.styleFrom(backgroundColor: AppColors.primary(context), foregroundColor: Colors.white)
            : ElevatedButton.styleFrom(
              backgroundColor:
                  Theme.of(context).brightness == Brightness.dark ? AppColors.cardBackground(context) : Colors.white,
              foregroundColor: AppColors.primary(context),
              side: BorderSide(color: AppColors.primary(context)),
            );

    final buttonChild = Row(
      mainAxisSize: isFullWidth ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (icon != null) ...[Icon(icon), const SizedBox(width: 8)],
        Text(label, style: MyFontStyle.buttonTextStyle),
      ],
    );

    return SizedBox(
      width: isFullWidth ? double.infinity : null,
      child: ElevatedButton(onPressed: onPressed, style: buttonStyle, child: buttonChild),
    );
  }
}
