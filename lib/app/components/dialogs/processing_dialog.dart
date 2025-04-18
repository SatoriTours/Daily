import 'dart:async';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/styles/dimensions.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 处理中对话框
///
/// 显示一个全屏的处理中对话框，带有动态沙漏图标和提示文本
/// 可以通过回调函数更新提示文本，并在处理完成后显示成功图标
class ProcessingDialog {
  /// 显示处理中对话框
  ///
  /// [message] 初始提示消息
  /// [barrierDismissible] 是否允许点击空白处关闭对话框
  /// [timeout] 超时时间（毫秒），如果设置，超过时间后将自动关闭对话框
  /// [onProcess] 处理函数，接收一个更新消息的函数作为参数
  static Future<T?> show<T>({
    String message = '处理中...',
    bool barrierDismissible = false,
    int? timeout,
    required Future<T> Function(void Function(String) updateMessage) onProcess,
  }) async {
    final messageRx = message.obs;
    final isCompleted = false.obs;
    final completer = Completer<T?>();

    // 显示对话框
    Get.dialog(
      _ProcessingDialogWidget(message: messageRx, isCompleted: isCompleted),
      barrierDismissible: barrierDismissible,
      barrierColor: Colors.black54,
    );

    // 设置超时
    Timer? timeoutTimer;
    if (timeout != null) {
      timeoutTimer = Timer(Duration(milliseconds: timeout), () {
        if (!completer.isCompleted) {
          Get.back();
          completer.complete(null);
        }
      });
    }

    try {
      // 执行处理函数
      final result = await onProcess((newMessage) {
        messageRx.value = newMessage;
      });

      // 显示完成状态
      isCompleted.value = true;
      await Future.delayed(const Duration(milliseconds: 800));

      if (Get.isDialogOpen ?? false) {
        Get.back();
      }

      completer.complete(result);
      return result;
    } catch (e) {
      if (Get.isDialogOpen ?? false) {
        Get.back();
      }
      completer.completeError(e);
      rethrow;
    } finally {
      timeoutTimer?.cancel();
    }
  }
}

/// 处理中对话框组件
class _ProcessingDialogWidget extends StatelessWidget {
  final RxString message;
  final RxBool isCompleted;

  const _ProcessingDialogWidget({required this.message, required this.isCompleted});

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      elevation: 0,
      child: Center(
        child: Container(
          width: 160,
          padding: Dimensions.paddingCard,
          decoration: BoxDecoration(
            color: AppColors.cardBackground(context),
            borderRadius: BorderRadius.circular(Dimensions.radiusL),
            boxShadow: [
              BoxShadow(color: Colors.black.withValues(alpha: 0.2), blurRadius: 10, offset: const Offset(0, 4)),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Obx(() {
                if (isCompleted.value) {
                  return Container(
                    width: 60,
                    height: 60,
                    decoration: BoxDecoration(color: Colors.green.withValues(alpha: 0.1), shape: BoxShape.circle),
                    child: const Icon(Icons.check_circle_outline, color: Colors.green, size: 40),
                  );
                } else {
                  return SizedBox(
                    width: 60,
                    height: 60,
                    child: CircularProgressIndicator(
                      strokeWidth: 3,
                      valueColor: AlwaysStoppedAnimation<Color>(AppColors.primary(context)),
                    ),
                  );
                }
              }),
              Dimensions.verticalSpacerM,
              Obx(
                () => Text(
                  message.value,
                  textAlign: TextAlign.center,
                  style: MyFontStyle.bodyMedium.copyWith(color: AppColors.textPrimary(context)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
