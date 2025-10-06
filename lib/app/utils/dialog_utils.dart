import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 对话框工具类
class DialogUtils {
  // 私有构造函数，防止实例化
  DialogUtils._();

  // 加载对话框状态
  static bool _isLoadingShown = false;

  /// 显示提示对话框
  static Future<void> showAlert({
    required String title,
    required String message,
    String buttonText = '确定',
    VoidCallback? onConfirm,
  }) async {
    await Get.dialog(
      AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () {
              _closeDialog();
              if (onConfirm != null) onConfirm();
            },
            child: Text(buttonText),
          ),
        ],
      ),
    );
  }

  /// 显示确认对话框
  static Future<bool> showConfirm({
    required String title,
    required String message,
    String confirmText = '确定',
    String cancelText = '取消',
    VoidCallback? onConfirm,
    VoidCallback? onCancel,
  }) async {
    final result = await Get.dialog<bool>(
      AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () {
              _closeDialog();
              if (onCancel != null) onCancel();
            },
            child: Text(cancelText),
          ),
          TextButton(
            onPressed: () {
              _closeDialog();
              if (onConfirm != null) onConfirm();
            },
            child: Text(confirmText),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  /// 显示输入对话框
  static Future<String?> showInputDialog({
    required String title,
    String? initialValue,
    String hintText = '',
    String confirmText = '确定',
    String cancelText = '取消',
    TextInputType keyboardType = TextInputType.text,
  }) async {
    final TextEditingController controller = TextEditingController(text: initialValue);
    final result = await Get.dialog<String>(
      AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          decoration: InputDecoration(hintText: hintText),
          keyboardType: keyboardType,
        ),
        actions: [
          TextButton(onPressed: () => _closeDialog(), child: Text(cancelText)),
          TextButton(onPressed: () => _closeDialog(), child: Text(confirmText)),
        ],
      ),
    );
    return result;
  }

  /// 显示全屏加载提示
  static void showLoading({String tips = '', Color barrierColor = const Color(0x80000000)}) {
    if (_isLoadingShown) return; // 如果已经显示了loading，直接返回
    final context = Get.context;
    final textTheme = context != null ? Theme.of(context).textTheme.bodyMedium : null;
    Get.dialog(
      PopScope(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircularProgressIndicator(),
              if (tips.isNotEmpty) ...[const SizedBox(height: 16), Text(tips, style: textTheme)],
            ],
          ),
        ),
      ),
      barrierDismissible: false,
      barrierColor: barrierColor,
    );
    _isLoadingShown = true;
  }

  /// 隐藏加载提示
  static void hideLoading() {
    if (_isLoadingShown) {
      _closeDialog();
      _isLoadingShown = false;
    }
  }

  static void _closeDialog() {
    Navigator.of(Get.context!).pop();
  }
}
