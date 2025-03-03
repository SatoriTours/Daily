import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';

/// 自定义卡片
class CustomCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry? padding;
  final double elevation;

  const CustomCard({super.key, required this.child, this.onTap, this.padding, this.elevation = 2.0});

  @override
  Widget build(BuildContext context) {
    final cardChild = Padding(padding: padding ?? const EdgeInsets.all(16.0), child: child);

    return Card(
      elevation: elevation,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      color: AppColors.cardBackground(context),
      child:
          onTap != null ? InkWell(onTap: onTap, borderRadius: BorderRadius.circular(12), child: cardChild) : cardChild,
    );
  }
}
