import 'package:flutter/material.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

import '../../controllers/diary_controller.dart';
import 'diary_editor.dart';

/// 日记编辑对话框组件
class DiaryEditDialog extends StatelessWidget {
  final DiaryModel diary;
  final DiaryController controller;

  const DiaryEditDialog({super.key, required this.diary, required this.controller});

  @override
  Widget build(BuildContext context) {
    return DiaryEditor(controller: controller, diary: diary);
  }
}
