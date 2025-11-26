import 'package:flutter/material.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 加载指示器组件
///
/// 一个居中显示的加载指示器组件，包含：
/// - 圆形进度指示器（使用主题色）
/// - 可选的加载提示文本（支持国际化）
/// - 可配置的大小和样式
/// - 统一的视觉样式
///
/// 使用示例:
/// ```dart
/// // 基本用法
/// LoadingIndicator()
///
/// // 带消息
/// LoadingIndicator(messageKey: 'component.loading')
///
/// // 自定义大小
/// LoadingIndicator.small()
/// LoadingIndicator.large()
/// ```
class LoadingIndicator extends StatelessWidget {
  /// 加载提示文本（直接使用文本）
  final String? message;

  /// 加载提示文本国际化key
  final String? messageKey;

  /// 进度指示器大小
  final double? size;

  /// 线条宽度
  final double? strokeWidth;

  /// 是否使用小尺寸
  final bool isSmall;

  /// 是否使用大尺寸
  final bool isLarge;

  const LoadingIndicator({
    super.key,
    this.message,
    this.messageKey,
    this.size,
    this.strokeWidth,
    this.isSmall = false,
    this.isLarge = false,
  });

  /// 创建小尺寸加载指示器
  factory LoadingIndicator.small({
    String? message,
    String? messageKey,
  }) {
    return LoadingIndicator(
      message: message,
      messageKey: messageKey,
      size: Dimensions.iconSizeL,
      strokeWidth: 2.0,
      isSmall: true,
    );
  }

  /// 创建大尺寸加载指示器
  factory LoadingIndicator.large({
    String? message,
    String? messageKey,
  }) {
    return LoadingIndicator(
      message: message,
      messageKey: messageKey,
      size: 48,
      strokeWidth: 4.0,
      isLarge: true,
    );
  }

  /// 获取消息文本
  String? get _messageText {
    if (message != null) return message;
    if (messageKey != null) return messageKey!.t;
    return null;
  }

  /// 获取线条宽度
  double get _strokeWidth {
    if (strokeWidth != null) return strokeWidth!;
    if (isSmall) return 2.0;
    if (isLarge) return 4.0;
    return 3.0;
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(
            valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
            strokeWidth: _strokeWidth,
          ),
          if (_messageText != null) ...[
            SizedBox(height: isSmall ? Dimensions.spacingS : Dimensions.spacingM),
            Text(
              _messageText!,
              style: isSmall
                ? AppTypography.bodySmall
                : (isLarge ? AppTypography.bodyLarge : AppTypography.bodyMedium),
              textAlign: TextAlign.center,
            ),
          ],
        ],
      ),
    );
  }
}
