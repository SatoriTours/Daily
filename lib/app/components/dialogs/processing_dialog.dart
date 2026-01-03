import 'dart:async';
import 'package:flutter/material.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

/// 处理中对话框
///
/// 简化的处理中对话框，显示加载状态和处理消息。
/// 适合长时间运行的操作，提供用户友好的等待界面。
///
/// 使用示例：
/// ```dart
/// final result = await ProcessingDialog.show(
///   context: context,
///   messageKey: 'component.ai_analyzing',
///   onProcess: () async {
///     // 执行耗时操作
///     return await someAsyncOperation();
///   },
/// );
/// ```
class ProcessingDialog {
  /// 显示处理中对话框
  ///
  /// [context] 上下文
  /// [message] 提示消息文本
  /// [messageKey] 提示消息国际化key
  /// [barrierDismissible] 是否允许点击空白处关闭对话框
  /// [timeout] 超时时间（毫秒）
  /// [onProcess] 处理函数
  static Future<T?> show<T>({
    required BuildContext context,
    String? message,
    String? messageKey,
    bool barrierDismissible = false,
    int? timeout,
    required Future<T> Function() onProcess,
  }) async {
    final displayMessage = message ?? (messageKey?.t ?? 'component.processing'.t);
    final completer = Completer<T?>();

    // 显示对话框
    showDialog(
      context: context,
      barrierDismissible: barrierDismissible,
      barrierColor: Colors.black.withValues(alpha: 0.5),
      builder: (context) => _ProcessingDialogWidget(
        message: displayMessage,
      ),
    );

    // 设置超时
    Timer? timeoutTimer;
    if (timeout != null) {
      timeoutTimer = Timer(Duration(milliseconds: timeout), () {
        if (!completer.isCompleted && context.mounted) {
          AppNavigation.back();
          completer.complete(null);
        }
      });
    }

    try {
      // 执行处理函数
      final result = await onProcess();

      // 关闭对话框
      if (context.mounted) {
        AppNavigation.back();
      }
      completer.complete(result);
      return result;
    } catch (e) {
      if (context.mounted) {
        AppNavigation.back();
      }
      completer.completeError(e);
      rethrow;
    } finally {
      timeoutTimer?.cancel();
    }
  }

  /// 显示简单的处理中对话框（仅显示加载指示器）
  static Future<T?> showSimple<T>({
    required BuildContext context,
    required Future<T> Function() onProcess,
    String? messageKey,
  }) {
    return show<T>(
      context: context,
      messageKey: messageKey ?? 'component.processing',
      onProcess: onProcess,
    );
  }
}
/// 处理中对话框组件
class _ProcessingDialogWidget extends StatelessWidget {
  final String message;

  const _ProcessingDialogWidget({
    required this.message,
  });

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      elevation: 0,
      child: Center(
        child: Container(
          width: 180,
          padding: Dimensions.paddingCard,
          decoration: StyleGuide.getCardDecoration(context),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // 加载指示器
              SizedBox(
                width: 60,
                height: 60,
                child: CircularProgressIndicator(
                  strokeWidth: 4,
                  valueColor: AlwaysStoppedAnimation<Color>(AppColors.getPrimary(context)),
                ),
              ),
              Dimensions.verticalSpacerM,
              // 消息文本
              Text(
                message,
                textAlign: TextAlign.center,
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.getOnSurface(context),
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }
}