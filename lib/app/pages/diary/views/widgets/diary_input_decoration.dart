import 'package:flutter/material.dart';

/// 日记输入框装饰提供者
class DiaryInputDecoration {
  /// 获取日记输入框的装饰
  static InputDecoration get(BuildContext context) {
    return InputDecoration(
      hintText: '记录现在，畅想未来...',
      hintStyle: TextStyle(
        color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[600] : Colors.grey[400],
      ),
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      errorBorder: InputBorder.none,
      disabledBorder: InputBorder.none,
      contentPadding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
    );
  }
}
