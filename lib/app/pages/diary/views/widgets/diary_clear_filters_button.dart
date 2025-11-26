import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 日记清除筛选按钮
class DiaryClearFiltersButton extends StatelessWidget {
  final VoidCallback onPressed;
  final String text;

  const DiaryClearFiltersButton({super.key, required this.onPressed, this.text = '清除筛选'});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      child: InkWell(
        onTap: onPressed,
        child: Container(
          alignment: Alignment.center,
          padding: const EdgeInsets.symmetric(vertical: 12),
          decoration: BoxDecoration(
            color: DiaryStyle.inputBackgroundColor(context),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Text(text, style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontWeight: FontWeight.w500)),
        ),
      ),
    );
  }
}
