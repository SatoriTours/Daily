import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 自定义标签
class CustomChip extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onTap;
  final bool isSelected;

  const CustomChip({super.key, required this.label, this.icon, this.onTap, this.isSelected = false});

  @override
  Widget build(BuildContext context) {
    final backgroundColor = isSelected ? AppColors.primary(context) : AppColors.primary(context).withAlpha(26);

    final textColor = isSelected ? Colors.white : AppColors.primary(context);

    final iconColor = isSelected ? Colors.white : AppColors.primary(context);

    return InkWell(
      onTap: onTap,
      child: Chip(
        backgroundColor: backgroundColor,
        avatar: icon != null ? Icon(icon, size: 16, color: iconColor) : null,
        label: Text(label, style: MyFontStyle.chipTextStyle.copyWith(color: textColor)),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
    );
  }
}
