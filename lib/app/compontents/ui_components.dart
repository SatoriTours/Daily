import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 通用UI组件库，提供整个应用中可重用的UI组件

/// 自定义分隔线
class CustomDivider extends StatelessWidget {
  final double height;
  final double indent;
  final double endIndent;

  const CustomDivider({super.key, this.height = 1.0, this.indent = 0.0, this.endIndent = 0.0});

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: height,
      thickness: 1,
      color: AppColors.divider(context),
      indent: indent,
      endIndent: endIndent,
    );
  }
}

/// 自定义空状态提示
class EmptyStateWidget extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? subtitle;
  final Widget? action;

  const EmptyStateWidget({super.key, required this.icon, required this.title, this.subtitle, this.action});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 64, color: AppColors.textSecondary(context).withOpacity(0.5)),
            const SizedBox(height: 16),
            Text(title, style: MyFontStyle.emptyStateStyleThemed(context), textAlign: TextAlign.center),
            if (subtitle != null) ...[
              const SizedBox(height: 8),
              Text(subtitle!, style: MyFontStyle.cardSubtitleStyleThemed(context), textAlign: TextAlign.center),
            ],
            if (action != null) ...[const SizedBox(height: 24), action!],
          ],
        ),
      ),
    );
  }
}

/// 自定义标签
class CustomChip extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onTap;
  final bool isSelected;

  const CustomChip({super.key, required this.label, this.icon, this.onTap, this.isSelected = false});

  @override
  Widget build(BuildContext context) {
    final backgroundColor = isSelected ? AppColors.primary(context) : AppColors.primary(context).withOpacity(0.1);

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
