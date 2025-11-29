import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';

/// 日记工具栏按钮
class DiaryToolbarButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;
  final bool isAccent;

  const DiaryToolbarButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.isAccent = false,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Container(
        width: 36,
        height: 36,
        margin: const EdgeInsets.symmetric(horizontal: 2),
        child: IconButton(
          onPressed: onPressed,
          icon: Icon(
            icon,
            size: 16,
            color: isAccent ? DiaryStyles.getAccentColor(context) : DiaryStyles.getPrimaryTextColor(context),
          ),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }
}
