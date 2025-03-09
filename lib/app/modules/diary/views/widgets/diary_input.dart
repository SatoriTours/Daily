import 'package:flutter/material.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

import '../../controllers/diary_controller.dart';

/// 日记输入组件 - 支持深色/浅色主题
class DiaryInput extends StatelessWidget {
  final DiaryController controller;

  const DiaryInput({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: DiaryStyle.cardColor(context),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(Theme.of(context).brightness == Brightness.dark ? 0.3 : 0.05),
            blurRadius: 8,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: DiaryStyle.inputBackgroundColor(context),
                borderRadius: BorderRadius.circular(24),
              ),
              child: TextField(
                controller: controller.contentController,
                decoration: InputDecoration(
                  hintText: '写点什么...',
                  hintStyle: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                    fontSize: 14,
                  ),
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                ),
                style: TextStyle(fontSize: 14, color: DiaryStyle.primaryTextColor(context)),
                maxLines: 1,
                textInputAction: TextInputAction.send,
                onSubmitted: (value) {
                  if (value.trim().isNotEmpty) {
                    controller.createDiary(value);
                  }
                },
              ),
            ),
          ),
          const SizedBox(width: 12),
          Container(
            decoration: BoxDecoration(color: DiaryStyle.accentColor(context), shape: BoxShape.circle),
            child: IconButton(
              icon: const Icon(Icons.send, color: Colors.white, size: 20),
              onPressed: () {
                final content = controller.contentController.text;
                if (content.trim().isNotEmpty) {
                  controller.createDiary(content);
                }
              },
            ),
          ),
        ],
      ),
    );
  }
}
