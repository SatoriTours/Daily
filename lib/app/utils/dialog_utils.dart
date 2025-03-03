import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 对话框工具类
class DialogUtils {
  // 私有构造函数，防止实例化
  DialogUtils._();

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
              Get.back();
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
              Get.back(result: false);
              Get.close();
              if (onCancel != null) onCancel();
            },
            child: Text(cancelText),
          ),
          TextButton(
            onPressed: () {
              Get.back(result: true);
              Get.close();
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
          TextButton(onPressed: () => Get.back(), child: Text(cancelText)),
          TextButton(onPressed: () => Get.back(result: controller.text), child: Text(confirmText)),
        ],
      ),
    );
    return result;
  }
}
