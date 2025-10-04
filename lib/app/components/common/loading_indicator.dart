import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 自定义加载指示器
///
/// 一个居中显示的加载指示器组件，包含：
/// - 圆形进度指示器
/// - 可选的加载提示文本
///
/// 使用示例:
/// ```dart
/// LoadingIndicator(message: '加载中...')
/// ```
class LoadingIndicator extends StatelessWidget {
  /// 加载提示文本
  ///
  /// 如果为 null，则只显示圆形进度指示器
  final String? message;

  /// 创建一个加载指示器
  ///
  /// [message] 可选的加载提示文本
  const LoadingIndicator({super.key, this.message});
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context))),
          if (message != null) ...[const SizedBox(height: 16), Text(message!, style: AppTypography.bodyMedium)],
        ],
      ),
    );
  }
}
