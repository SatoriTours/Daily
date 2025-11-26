import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';

import 'markdown_toolbar.dart';
import 'diary_toolbar_button.dart';

/// 日记工具栏
class DiaryToolbar extends StatelessWidget {
  final TextEditingController controller;
  final Function() onImagePick;
  final Function() onSave;
  final String saveLabel;

  const DiaryToolbar({
    super.key,
    required this.controller,
    required this.onImagePick,
    required this.onSave,
    this.saveLabel = '保存',
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      margin: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          // Markdown 工具栏
          Expanded(
            child: MarkdownToolbar(
              controller: controller,
              onSave: null, // 不使用工具栏的保存功能
            ),
          ),

          // 图片添加按钮
          DiaryToolbarButton(icon: FeatherIcons.image, tooltip: '添加图片', onPressed: onImagePick, isAccent: false),

          // 保存/更新按钮
          DiaryToolbarButton(icon: FeatherIcons.check, tooltip: saveLabel, onPressed: onSave, isAccent: true),
        ],
      ),
    );
  }
}
