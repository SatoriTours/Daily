import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';

/// 搜索输入框组件
class SearchTextField extends StatelessWidget {
  final TextEditingController controller;
  final String hintText;
  final VoidCallback? onClear;
  final ValueChanged<String>? onSubmitted;
  final bool isVisible;
  final EdgeInsetsGeometry? margin;

  const SearchTextField({
    super.key,
    required this.controller,
    this.hintText = '搜索',
    this.onClear,
    this.onSubmitted,
    this.isVisible = true,
    this.margin,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedSlide(
      offset: Offset(0, isVisible ? 0 : -1),
      duration: const Duration(milliseconds: 300),
      child: Container(
        height: 44,
        margin: margin ?? const EdgeInsets.fromLTRB(16, 8, 16, 8),
        child: TextField(
          controller: controller,
          decoration: InputDecoration(
            hintText: hintText,
            hintStyle: TextStyle(fontSize: 14, color: AppColors.textSecondary(context)),
            prefixIcon: Icon(Icons.search, size: 20, color: AppColors.textSecondary(context)),
            suffixIcon: IconButton(
              icon: Icon(Icons.clear, size: 18, color: AppColors.textSecondary(context)),
              onPressed: () {
                controller.clear();
                if (onClear != null) {
                  onClear!();
                }
              },
            ),
            filled: true,
            fillColor:
                Theme.of(context).brightness == Brightness.dark
                    ? AppColors.cardBackgroundDark.withOpacity(0.8)
                    : Colors.grey.shade100,
            contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
            enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(22), borderSide: BorderSide.none),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(22),
              borderSide: BorderSide(color: AppColors.primary(context), width: 1),
            ),
          ),
          onSubmitted: onSubmitted,
        ),
      ),
    );
  }
}
