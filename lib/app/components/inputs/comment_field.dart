import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 评论输入框组件
///
/// 一个带有统一样式的多行文本输入框，用于输入评论或备注
///
/// 使用示例:
/// ```dart
/// CommentField(
///   controller: myController,
///   hintText: "添加备注信息（可选）",
/// )
/// ```
class CommentField extends StatelessWidget {
  /// 文本编辑控制器
  final TextEditingController controller;

  /// 提示文本
  final String hintText;

  /// 最小行数
  final int minLines;

  /// 是否自动获取焦点
  final bool autofocus;

  /// 键盘类型
  final TextInputType keyboardType;

  /// 输入动作类型
  final TextInputAction textInputAction;

  /// 内容变化回调
  final ValueChanged<String>? onChanged;

  /// 提交回调
  final ValueChanged<String>? onSubmitted;

  /// 自定义内容填充
  final EdgeInsetsGeometry? contentPadding;

  /// 创建一个评论输入框
  ///
  /// [controller] 文本编辑控制器
  /// [hintText] 提示文本，默认为"添加备注信息（可选）"
  /// [minLines] 最小行数，默认为5
  /// [autofocus] 是否自动获取焦点，默认为false
  /// [keyboardType] 键盘类型，默认为多行文本
  /// [textInputAction] 输入动作类型，默认为换行
  /// [onChanged] 内容变化回调
  /// [onSubmitted] 提交回调
  /// [contentPadding] 内容填充
  const CommentField({
    super.key,
    required this.controller,
    this.hintText = "添加备注信息（可选）",
    this.minLines = 5,
    this.autofocus = false,
    this.keyboardType = TextInputType.multiline,
    this.textInputAction = TextInputAction.newline,
    this.onChanged,
    this.onSubmitted,
    this.contentPadding,
  });
  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 120),
      child: TextField(
        controller: controller,
        decoration: _buildInputDecoration(context),
        style: AppTypography.bodyMedium,
        minLines: minLines,
        maxLines: null, // 允许文本框垂直扩展
        keyboardType: keyboardType,
        textInputAction: textInputAction,
        autofocus: autofocus,
        onChanged: onChanged,
        onSubmitted: onSubmitted,
      ),
    );
  }

  /// 构建输入框装饰
  InputDecoration _buildInputDecoration(BuildContext context) {
    return InputDecoration(
      hintText: hintText,
      hintStyle: AppTypography.bodyMedium.copyWith(
        color: AppColors.getOnSurfaceVariant(context).withValues(alpha: 0.6),
      ),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Dimensions.radiusM),
        borderSide: BorderSide.none,
      ),
      contentPadding: contentPadding ?? Dimensions.paddingM,
      filled: true,
      fillColor: Theme.of(
        context,
      ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.1),
    );
  }
}
