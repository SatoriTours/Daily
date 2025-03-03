import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/app_theme.dart';

/// 操作按钮组件
class ActionButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final Color? color;
  final double size;
  final String? tooltip;

  const ActionButton({super.key, required this.icon, required this.onTap, this.color, this.size = 16, this.tooltip});

  @override
  Widget build(BuildContext context) {
    final colorScheme = AppTheme.getColorScheme(context);
    final buttonColor = color ?? colorScheme.onSurfaceVariant.withOpacity(0.7);

    final button = InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: 28,
        height: 24,
        alignment: Alignment.center,
        child: Icon(icon, size: size, color: buttonColor),
      ),
    );

    if (tooltip != null) {
      return Tooltip(message: tooltip!, child: button);
    }

    return button;
  }
}
