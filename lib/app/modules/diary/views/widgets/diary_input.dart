import 'package:flutter/material.dart';
import 'package:daily_satori/app_exports.dart';

import '../../controllers/diary_controller.dart';

/// 日记输入组件
class DiaryInput extends StatelessWidget {
  final DiaryController controller;

  const DiaryInput({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 10, offset: const Offset(0, -3))],
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: controller.contentController,
              decoration: const InputDecoration(
                hintText: '写点什么...',
                border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(24))),
                contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              ),
              maxLines: 1,
              textInputAction: TextInputAction.send,
              onSubmitted: (value) {
                if (value.trim().isNotEmpty) {
                  controller.createDiary(value);
                }
              },
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            icon: const Icon(Icons.send),
            onPressed: () {
              final content = controller.contentController.text;
              if (content.trim().isNotEmpty) {
                controller.createDiary(content);
              }
            },
          ),
        ],
      ),
    );
  }
}
