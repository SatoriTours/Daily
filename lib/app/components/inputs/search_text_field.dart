import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// 搜索输入框组件
///
/// 一个带有动画效果的搜索输入框，支持以下特性：
/// - 可自定义提示文本
/// - 支持清除按钮
/// - 支持提交回调
/// - 支持显示/隐藏动画
/// - 可自定义外边距
///
/// 使用示例:
/// ```dart
/// SearchTextField(
///   controller: textController,
///   hintText: '搜索文章',
///   onSubmitted: (value) => print('搜索: $value'),
///   onClear: () => textController.clear(),
/// )
/// ```
class SearchTextField extends StatelessWidget {
  /// 文本输入控制器
  final TextEditingController controller;

  /// 提示文本
  ///
  /// 默认为 '搜索'
  final String hintText;

  /// 清除按钮回调
  ///
  /// 当点击清除按钮时触发
  final VoidCallback? onClear;

  /// 提交回调
  ///
  /// 当用户提交搜索时触发
  final ValueChanged<String>? onSubmitted;

  /// 是否可见
  ///
  /// 控制搜索框的显示/隐藏动画
  /// true 表示显示，false 表示隐藏
  final bool isVisible;

  /// 外边距
  ///
  /// 可自定义搜索框的外边距
  /// 默认为 EdgeInsets.fromLTRB(16, 8, 16, 8)
  final EdgeInsetsGeometry? margin;

  /// 创建一个搜索输入框
  ///
  /// [controller] 文本输入控制器
  /// [hintText] 提示文本，默认为 '搜索'
  /// [onClear] 清除按钮回调，可选
  /// [onSubmitted] 提交回调，可选
  /// [isVisible] 是否可见，默认为 true
  /// [margin] 外边距，可选
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
      duration: AnimationConfig.duration,
      child: _buildSearchField(context),
    );
  }

  /// 构建搜索输入框
  Widget _buildSearchField(BuildContext context) {
    return Container(
      height: 44,
      margin: margin ?? const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: TextField(
        controller: controller,
        decoration: InputStyles.getSearchDecoration(context, hintText: hintText).copyWith(
          suffixIcon: controller.text.isNotEmpty
              ? IconButton(
                  icon: const Icon(Icons.clear),
                  onPressed: () {
                    controller.clear();
                    onClear?.call();
                  },
                )
              : null,
        ),
        onSubmitted: onSubmitted,
      ),
    );
  }
}
