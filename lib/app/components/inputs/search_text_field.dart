import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/component_style.dart';

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
          decoration: ComponentStyle.searchInputDecoration(
            context,
            hintText: hintText,
            onClear: onClear,
            controller: controller,
          ),
          onSubmitted: onSubmitted,
        ),
      ),
    );
  }
}
