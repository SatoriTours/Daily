import 'package:flutter/material.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/pages/diary_styles.dart';
import 'package:feather_icons/feather_icons.dart';

import '../../controllers/diary_controller.dart';
import 'diary_editor.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

/// 日记输入组件 - 支持Markdown和图片
class DiaryInput extends StatelessWidget {
  final DiaryController controller;

  const DiaryInput({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: DiaryStyles.getCardBackgroundColor(context),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(Theme.of(context).brightness == Brightness.dark ? 77 : 13),
            blurRadius: 8,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: InkWell(
        onTap: () => _showExpandedEditor(context),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          decoration: BoxDecoration(
            color: DiaryStyles.getInputBackgroundColor(context),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  '记录现在，畅想未来',
                  style: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                    fontSize: 14,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),
              Icon(FeatherIcons.edit2, color: DiaryStyles.getSecondaryTextColor(context), size: 18),
            ],
          ),
        ),
      ),
    );
  }

  // 显示扩展编辑器
  void _showExpandedEditor(BuildContext context) {
    // 清空内容控制器
    controller.contentController.clear();

    // 显示底部编辑器模态框
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => DiaryEditor(controller: controller),
    );
  }
}
